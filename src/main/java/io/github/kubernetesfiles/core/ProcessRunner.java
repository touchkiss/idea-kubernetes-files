package io.github.kubernetesfiles.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/** Owns subprocess streams; caller owns input/output. Never allocates file-sized buffers. */
public class ProcessRunner {
    private static final int CAPTURE_LIMIT = 16 * 1024 * 1024;

    public byte[] run(List<String> command, InputStream input, OutputStream output,
                      Progress progress, Duration timeout) throws IOException {
        progress.checkCancelled();
        Process process = new ProcessBuilder(command).start();
        ExecutorService io = Executors.newFixedThreadPool(3, task -> {
            Thread thread = new Thread(task, "kubernetes-files-io"); thread.setDaemon(true); return thread;
        });
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        OutputStream sink = output == null ? captured : output;
        Future<?> writer = io.submit(() -> {
            try (OutputStream stdin = process.getOutputStream()) {
                if (input != null) copy(input, stdin, progress, true, false);
            } catch (IOException e) { throw new UncheckedIOException(e); }
        });
        Future<?> reader = io.submit(() -> {
            try (InputStream stdout = process.getInputStream()) {
                copy(stdout, sink, progress, input == null && output != null, output == null);
            } catch (IOException e) { throw new UncheckedIOException(e); }
        });
        Future<?> stderr = io.submit(() -> {
            try (InputStream stream = process.getErrorStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = stream.read(buffer)) != -1) {
                    int keep = Math.min(n, Math.max(0, 65536 - errors.size()));
                    errors.write(buffer, 0, keep);
                }
            } catch (IOException e) { throw new UncheckedIOException(e); }
        });
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (!process.waitFor(50, TimeUnit.MILLISECONDS)) {
                progress.checkCancelled();
                if (System.nanoTime() >= deadline) throw new IOException("命令超时（" + timeout.toSeconds() + " 秒）");
                // If a pipe failed, don't wait forever for a producer blocked on that pipe.
                if (reader.isDone()) checkFuture(reader);
                if (writer.isDone()) checkFuture(writer);
            }
            progress.checkCancelled();
            checkFuture(stderr);
            if (process.exitValue() != 0) {
                throw new IOException("kubectl/远端命令失败，退出码 " + process.exitValue() + ":\n" + errors.toString(StandardCharsets.UTF_8));
            }
            checkFuture(writer);
            checkFuture(reader);
            progress.checkCancelled();
            return captured.toByteArray();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new CancellationException("操作已取消");
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            try { process.getOutputStream().close(); } catch (IOException ignored) {}
            try { process.getInputStream().close(); } catch (IOException ignored) {}
            try { process.getErrorStream().close(); } catch (IOException ignored) {}
            io.shutdownNow();
        }
    }

    private static void checkFuture(Future<?> future) throws IOException, InterruptedException {
        try { future.get(5, TimeUnit.SECONDS); }
        catch (ExecutionException e) {
            if (e.getCause() instanceof UncheckedIOException io) throw io.getCause();
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IOException(e.getCause());
        } catch (TimeoutException e) { throw new IOException("等待传输管道结束超时", e); }
    }

    private static void copy(InputStream input, OutputStream output, Progress progress,
                             boolean report, boolean bounded) throws IOException {
        byte[] buffer = new byte[65536]; long total = 0; int n;
        while ((n = input.read(buffer)) != -1) {
            progress.checkCancelled(); total += n;
            if (bounded && total > CAPTURE_LIMIT) throw new IOException("目录/命令输出超过 16 MiB，请缩小目录范围");
            output.write(buffer, 0, n);
            if (report) progress.transferred(total);
        }
        output.flush();
    }
}
