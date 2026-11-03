package io.github.kubernetesfiles.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;

public class KubectlClient {
    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration TRANSFER_TIMEOUT = Duration.ofHours(2);
    private final Target target;
    private final ProcessRunner runner = new ProcessRunner();
    private static final String HASH_FUNCTION = """
        hash_file() {
          if command -v sha256sum >/dev/null 2>&1; then sha256sum < "$1";
          elif command -v shasum >/dev/null 2>&1; then shasum -a 256 < "$1";
          else echo '容器缺少 sha256sum/shasum，无法校验文件' >&2; return 1; fi
        }
        """;

    public KubectlClient(Target target) { this.target = target; }

    public List<String> command(String script, String... arguments) {
        List<String> args = baseCommand(target.kubectl(), target.kubeconfig());
        args.addAll(List.of("--context",target.context(),"--namespace",target.namespace(),"exec","-i",target.pod(),"-c",target.container(),"--","sh","-c",script,"kubernetes-files"));
        args.addAll(List.of(arguments));
        return args;
    }

    public static List<String> baseCommand(String kubectl, String kubeconfig) {
        List<String> args = new ArrayList<>(); args.add(kubectl);
        if (kubeconfig != null && !kubeconfig.isBlank()) args.addAll(List.of("--kubeconfig",kubeconfig));
        return args;
    }

    public record DirectoryListing(String directory,List<RemoteEntry> entries,String notice) {}

    /** Non-interactive exec cwd, not the current directory of an existing terminal. */
    public String workingDirectory(Progress progress)throws IOException {
        String result=new String(runner.run(command("pwd -P"),null,null,progress,Duration.ofSeconds(10)),StandardCharsets.UTF_8);
        // Remove only pwd's line terminator; whitespace may be part of the directory name.
        if(result.endsWith("\n"))result=result.substring(0,result.length()-1);
        try{requireAbsolute(result);}catch(IllegalArgumentException e){throw new IOException("容器工作目录输出无效",e);}
        return result;
    }

    public DirectoryListing openInitialDirectory(String requested,Progress progress)throws IOException {
        progress.checkCancelled();
        if(requested!=null && !requested.isBlank()) {
            requireAbsolute(requested);
            return new DirectoryListing(requested,list(requested,progress),"");
        }
        String detected=null;
        try {
            detected=workingDirectory(progress);
            return new DirectoryListing(detected,list(detected,progress),"");
        } catch(IOException e) {
            progress.checkCancelled();
            if("/".equals(detected))throw e;
            return new DirectoryListing("/",list("/",progress),"自动工作目录不可用，已回退到 /："+e.getMessage());
        }
    }

    public List<RemoteEntry> list(String directory, Progress progress) throws IOException {
        requireAbsolute(directory);
        String script = """
            set -eu
            cd "$1"
            find . ! -name . -prune -exec sh -c '
              for p do
                n=${p#./}
                if [ -L "$p" ]; then t=l; s=0
                elif [ -d "$p" ]; then t=d; s=0
                elif [ -f "$p" ]; then t=f; s=$(wc -c < "$p")
                else t=o; s=0; fi
                printf "%s\\000%s\\000%s\\000" "$n" "$t" "$s"
              done
            ' sh {} +
            """;
        String data = new String(runner.run(command(script,directory),null,null,progress,METADATA_TIMEOUT),StandardCharsets.UTF_8);
        if (data.isEmpty()) return List.of();
        String[] fields = data.split("\u0000",-1);
        if ((fields.length-1)%3 != 0) throw new IOException("远端目录输出格式不正确");
        List<RemoteEntry> entries = new ArrayList<>();
        for (int i=0;i<fields.length-1;i+=3) {
            try { entries.add(new RemoteEntry(fields[i],fields[i+1],Long.parseLong(fields[i+2].trim()))); }
            catch (NumberFormatException e) { throw new IOException("文件大小解析失败",e); }
        }
        entries.sort(Comparator.comparing(RemoteEntry::directory).reversed().thenComparing(RemoteEntry::name));
        return entries;
    }

    public boolean exists(String path, Progress progress) throws IOException {
        requireAbsolute(path);
        byte[] result=runner.run(command("if [ -e \"$1\" ] || [ -L \"$1\" ]; then printf yes; else printf no; fi",path),null,null,progress,METADATA_TIMEOUT);
        return new String(result,StandardCharsets.UTF_8).equals("yes");
    }

    public long size(String path, Progress progress) throws IOException {
        requireAbsolute(path);
        String script="set -eu; [ -f \"$1\" ] && [ ! -L \"$1\" ] || { echo '仅支持普通文件' >&2; exit 1; }; wc -c < \"$1\"";
        try { return Long.parseLong(new String(runner.run(command(script,path),null,null,progress,METADATA_TIMEOUT),StandardCharsets.UTF_8).trim()); }
        catch (NumberFormatException e) { throw new IOException("文件大小解析失败",e); }
    }

    public void upload(Path local, String remote, boolean replace, Progress progress) throws IOException {
        requireAbsolute(remote); progress.checkCancelled();
        if (!Files.isRegularFile(local,LinkOption.NOFOLLOW_LINKS)) throw new IOException("仅支持上传本地普通文件: "+local);
        String expected=hash(local,progress);
        String temporary=remote.substring(0,remote.lastIndexOf('/')+1)+".kfiles-"+UUID.randomUUID();
        String script="""
            set -eu
            target=$1; tmp=$2; expected=$3; replace=$4
            [ ! -L "$target" ] && { [ ! -e "$target" ] || [ -f "$target" ]; } || { echo '目标不是普通文件' >&2; exit 1; }
            if [ "$replace" != true ] && [ -e "$target" ]; then echo '目标已存在' >&2; exit 1; fi
            (set -C; : > "$tmp")
            trap 'rm -f -- "$tmp"' EXIT
            trap 'exit 130' INT TERM HUP
            cat > "$tmp"
            actual=$(hash_file "$tmp"); actual=${actual%% *}
            [ "$actual" = "$expected" ] || { echo 'SHA-256 校验失败' >&2; exit 1; }
            [ ! -L "$target" ] && { [ ! -e "$target" ] || [ -f "$target" ]; } || exit 1
            if [ "$replace" = true ]; then mv -fT -- "$tmp" "$target";
            else ln -T -- "$tmp" "$target" || { echo '目标已存在或无法创建' >&2; exit 1; }; rm -f -- "$tmp"; fi
            """;
        try (InputStream input=Files.newInputStream(local)) {
            runner.run(command(HASH_FUNCTION+script,remote,temporary,expected,Boolean.toString(replace)),input,null,progress,TRANSFER_TIMEOUT);
        } finally {
            // An exec disconnect may prevent a remote EXIT trap from running.
            try { runner.run(command("rm -f -- \"$1\"",temporary),null,null,Progress.NONE,Duration.ofSeconds(5)); }
            catch (IOException ignored) { /* Container may be gone; never replace the target during cleanup. */ }
        }
    }

    public void download(String remote, Path local, boolean replace, Progress progress) throws IOException {
        requireAbsolute(remote); progress.checkCancelled();
        Path destination=local.toAbsolutePath();
        validateLocalDestination(destination,replace);
        String expected=remoteHash(remote,progress);
        Path temporary=Files.createTempFile(destination.getParent(),".kfiles-",".part");
        try {
            try(OutputStream output=Files.newOutputStream(temporary)) {
                runner.run(command("set -eu; [ -f \"$1\" ] && [ ! -L \"$1\" ] || exit 1; cat -- \"$1\"",remote),null,output,progress,TRANSFER_TIMEOUT);
            }
            if (!expected.equals(hash(temporary,progress))) throw new IOException("SHA-256 校验失败，远程文件可能在下载期间发生变化");
            validateLocalDestination(destination,replace);
            progress.checkCancelled();
            if (replace) Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary,destination);
        } finally { Files.deleteIfExists(temporary); }
    }

    private String remoteHash(String remote,Progress progress) throws IOException {
        String script="set -eu; [ -f \"$1\" ] && [ ! -L \"$1\" ] || { echo '仅支持下载普通文件' >&2; exit 1; }; hash_file \"$1\"";
        String result=new String(runner.run(command(HASH_FUNCTION+script,remote),null,null,progress,TRANSFER_TIMEOUT),StandardCharsets.UTF_8).trim();
        String digest=result.split("\\s+")[0];
        if (!digest.matches("[a-fA-F0-9]{64}")) throw new IOException("远端 SHA-256 输出不正确");
        return digest.toLowerCase(Locale.ROOT);
    }

    private static void validateLocalDestination(Path path,boolean replace) throws IOException {
        if (Files.isSymbolicLink(path) || Files.isDirectory(path)) throw new IOException("目标不能是目录或符号链接: "+path);
        if (!replace && Files.exists(path,LinkOption.NOFOLLOW_LINKS)) throw new IOException("本地目标已存在: "+path);
    }

    private static String hash(Path file, Progress progress) throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream input=Files.newInputStream(file)) {
                byte[] buffer=new byte[65536]; int n;
                while((n=input.read(buffer))!=-1) {progress.checkCancelled();digest.update(buffer,0,n);}
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }

    public static String join(String directory,String name) {
        if(name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.indexOf(0)>=0) throw new IllegalArgumentException("非法文件名");
        return (directory.endsWith("/")?directory:directory+"/")+name;
    }

    private static void requireAbsolute(String path) {
        if(path==null || !path.startsWith("/") || path.indexOf(0)>=0) throw new IllegalArgumentException("远程路径必须是绝对路径");
    }
}
