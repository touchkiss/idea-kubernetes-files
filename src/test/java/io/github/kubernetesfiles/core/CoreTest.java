package io.github.kubernetesfiles.core;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class CoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private Path root;
    private KubectlClient client;
    @Before public void setup() throws Exception {
        root = folder.getRoot().toPath();
        Path fake = root.resolve("fake kubectl");
        Files.writeString(fake, "#!/bin/sh\nwhile [ \"$1\" != \"--\" ]; do shift; done\nshift\nexec \"$@\"\n");
        fake.toFile().setExecutable(true);
        // BSD macOS mv/ln lack Linux -T. This test adapter models Linux rename/link
        // semantics and can inject a destination race immediately before commit.
        Path bin=Files.createDirectory(root.resolve("bin"));
        String shim="""
            #!/usr/bin/python3
            import os, sys, pathlib
            mode=sys.argv[1]; args=sys.argv[2:]
            if args and args[0]=='--': args=args[1:]
            source,target=args
            root=pathlib.Path(os.environ['KF_TEST_ROOT'])
            race=root/'race-directory'
            if race.exists():
                os.unlink(target);os.mkdir(target);race.unlink()
            if '-T' in mode or mode.endswith('T'):
                if pathlib.Path(sys.argv[0]).name=='mv': os.replace(source,target)
                else: os.link(source,target)
            else:
                os.execv('/bin/'+pathlib.Path(sys.argv[0]).name,[pathlib.Path(sys.argv[0]).name,mode,'--',source,target])
            """;
        for(String cmd:List.of("mv","ln")){Path tool=bin.resolve(cmd);Files.writeString(tool,shim);tool.toFile().setExecutable(true);}
        Files.writeString(fake,"#!/bin/sh\nKF_TEST_ROOT=$(CDPATH= cd -- \"$(dirname -- \"$0\")\" && pwd)\nexport KF_TEST_ROOT\nPATH=\"$KF_TEST_ROOT/bin:$PATH\"; export PATH\nwhile [ \"$1\" != \"--\" ]; do shift; done\nshift\nexec \"$@\"\n");
        client = new KubectlClient(new Target(fake.toString(), "", "test", "ns", "pod", "container"));
    }
    @Test public void listPreservesSpecialNamesAndTypes() throws Exception {
        String name = "file ' \n $(touch NEVER)";
        Files.writeString(root.resolve(name), "abc");
        Files.createDirectory(root.resolve("dir"));
        Files.createSymbolicLink(root.resolve("link"), root.resolve(name));
        var entries = client.list(root.toString(), Progress.NONE);
        assertTrue(entries.contains(new RemoteEntry(name,"f",3)));
        assertTrue(entries.contains(new RemoteEntry("dir","d",0)));
        assertTrue(entries.contains(new RemoteEntry("link","l",0)));
        assertFalse(Files.exists(root.resolve("NEVER")));
    }
    @Test public void initialDirectoryDetectsContainerWorkingDirectoryWithoutTrimmingPath() throws Exception {
        Path work=Files.createDirectory(root.resolve("start dir\n"));
        Files.writeString(work.resolve("found.txt"),"ok");
        Path fake=Path.of(client.command("").getFirst());
        Files.writeString(fake,Files.readString(fake).replace("while [", "cd \"$KF_TEST_ROOT/start dir\n\"\nwhile ["));
        var result=client.openInitialDirectory("",Progress.NONE);
        assertEquals(work.toRealPath().toString(),result.directory());
        assertTrue(result.entries().stream().anyMatch(e->e.name().equals("found.txt")));
        assertEquals("",result.notice());
    }
    @Test public void configuredInitialDirectoryOverridesDetectionAndDoesNotFallBackOnFailure() throws Exception {
        var fixed=new KubectlClient(new Target("kubectl","","ctx","ns","pod","app")){
            @Override public String workingDirectory(Progress p){fail("Configured directory must skip detection");return "/";}
            @Override public List<RemoteEntry> list(String directory,Progress p)throws IOException{
                if(directory.equals("/missing"))throw new IOException("missing directory");
                return List.of();
            }
        };
        assertEquals("/data1/weibo/bin",fixed.openInitialDirectory("/data1/weibo/bin",Progress.NONE).directory());
        assertThrows(IOException.class,()->fixed.openInitialDirectory("/missing",Progress.NONE));
        assertThrows(IllegalArgumentException.class,()->fixed.openInitialDirectory("relative",Progress.NONE));
    }
    @Test public void automaticDirectoryFailureFallsBackToRootButCancellationPropagates() throws Exception {
        var fallback=new KubectlClient(new Target("kubectl","","ctx","ns","pod","app")){
            @Override public String workingDirectory(Progress p)throws IOException{p.checkCancelled();throw new IOException("pwd unavailable");}
            @Override public List<RemoteEntry> list(String directory,Progress p){assertEquals("/",directory);return List.of();}
        };
        var result=fallback.openInitialDirectory("",Progress.NONE);
        assertEquals("/",result.directory());assertTrue(result.notice().contains("回退"));
        assertThrows(CancellationException.class,()->fallback.openInitialDirectory("",new Progress(){
            @Override public void checkCancelled(){throw new CancellationException();}
        }));
    }
    @Test public void unreadableDetectedDirectoryFallsBackToRoot() throws Exception {
        var fallback=new KubectlClient(new Target("kubectl","","ctx","ns","pod","app")){
            @Override public String workingDirectory(Progress p){return "/unreadable";}
            @Override public List<RemoteEntry> list(String directory,Progress p)throws IOException{
                if(directory.equals("/unreadable"))throw new IOException("permission denied");
                assertEquals("/",directory);return List.of();
            }
        };
        var result=fallback.openInitialDirectory("",Progress.NONE);
        assertEquals("/",result.directory());assertTrue(result.notice().contains("permission denied"));
    }
    @Test public void binaryRoundTripAndProgress() throws Exception {
        byte[] bytes = new byte[250_123]; new Random(7).nextBytes(bytes);
        Path source = root.resolve("source"); Files.write(source, bytes);
        Path remote = root.resolve("remote ' ; $(x).war");
        AtomicLong transferred = new AtomicLong();
        client.upload(source, remote.toString(), false, new Progress(){ public void transferred(long n){transferred.set(n);} });
        assertArrayEquals(bytes, Files.readAllBytes(remote)); assertEquals(bytes.length, transferred.get());
        Path destination = root.resolve("download");
        client.download(remote.toString(), destination, false, Progress.NONE);
        assertArrayEquals(bytes, Files.readAllBytes(destination));
        assertNoPartials();
    }
    @Test public void emptyFileTransfers() throws Exception {
        Path source = root.resolve("empty"); Files.write(source,new byte[0]);
        Path remote = root.resolve("zero"); client.upload(source,remote.toString(),false,Progress.NONE);
        Path result = root.resolve("result"); client.download(remote.toString(),result,false,Progress.NONE);
        assertEquals(0,Files.size(result));
    }
    @Test public void uploadRefusesExistingAndAllowsExplicitReplace() throws Exception {
        Path source = root.resolve("source"); Files.writeString(source,"new");
        Path remote = root.resolve("target"); Files.writeString(remote,"old");
        assertThrows(IOException.class,()->client.upload(source,remote.toString(),false,Progress.NONE));
        assertEquals("old",Files.readString(remote));
        client.upload(source,remote.toString(),true,Progress.NONE);
        assertEquals("new",Files.readString(remote)); assertNoPartials();
    }
    @Test public void downloadRefusesExistingAndAllowsExplicitReplace() throws Exception {
        Path source = root.resolve("source"); Files.writeString(source,"new");
        Path target = root.resolve("local"); Files.writeString(target,"old");
        assertThrows(IOException.class,()->client.download(source.toString(),target,false,Progress.NONE));
        assertEquals("old",Files.readString(target));
        client.download(source.toString(),target,true,Progress.NONE);
        assertEquals("new",Files.readString(target));
    }
    @Test public void rejectsSymlinkAndDirectoryTargets() throws Exception {
        Path source = root.resolve("source"); Files.writeString(source,"old");
        Path link = root.resolve("link"); Files.createSymbolicLink(link,source);
        assertThrows(IOException.class,()->client.upload(source,link.toString(),true,Progress.NONE));
        assertThrows(IOException.class,()->client.download(source.toString(),link,true,Progress.NONE));
        assertThrows(IOException.class,()->client.download(link.toString(),root.resolve("out"),false,Progress.NONE));
        assertThrows(IOException.class,()->client.upload(source,root.toString(),true,Progress.NONE));
        assertEquals("old",Files.readString(source));
    }
    @Test public void failedDownloadKeepsOriginal() throws Exception {
        Path target = root.resolve("local"); Files.writeString(target,"old");
        assertThrows(IOException.class,()->client.download(root.resolve("missing").toString(),target,true,Progress.NONE));
        assertEquals("old",Files.readString(target)); assertNoPartials();
    }
    @Test public void existsHandlesDanglingSymlinks() throws Exception {
        Path link = root.resolve("dangling"); Files.createSymbolicLink(link,root.resolve("none"));
        assertTrue(client.exists(link.toString(),Progress.NONE));
        assertFalse(client.exists(root.resolve("absent").toString(),Progress.NONE));
    }
    @Test public void cancelledUploadKeepsOriginal() throws Exception {
        Path source = root.resolve("source"); Files.write(source,new byte[100_000]);
        Path target = root.resolve("target"); Files.writeString(target,"old");
        assertThrows(CancellationException.class,()->client.upload(source,target.toString(),true,new Progress(){ public void checkCancelled(){throw new CancellationException();} }));
        assertEquals("old",Files.readString(target)); assertNoPartials();
    }
    @Test public void runnerIncludesStderrOnFailure() throws Exception {
        IOException e=assertThrows(IOException.class,()->new ProcessRunner().run(List.of("sh","-c","echo forbidden >&2; exit 9"),null,null,Progress.NONE,Duration.ofSeconds(2)));
        assertTrue(e.getMessage().contains("forbidden")); assertTrue(e.getMessage().contains("9"));
    }
    @Test public void runnerTimeoutTerminatesProcess() {
        assertThrows(IOException.class,()->new ProcessRunner().run(List.of("sh","-c","sleep 30"),null,null,Progress.NONE,Duration.ofMillis(150)));
    }
    @Test public void validatesExplicitConnection() {
        assertThrows(IllegalArgumentException.class,()->new Target("kubectl","","","ns","pod","c"));
        assertThrows(IllegalArgumentException.class,()->new Target("kubectl","","test","ns","--help","c"));
    }
    @Test public void uploadsLongLegalFilename() throws Exception {
        Path source=root.resolve("source");Files.writeString(source,"new");
        Path target=root.resolve("x".repeat(220));
        client.upload(source,target.toString(),false,Progress.NONE);
        assertEquals("new",Files.readString(target));assertNoPartials();
    }
    @Test public void allExecCommandsBindExactTargetWithoutTty() {
        var args=client.command("printf '%s' \"$1\"","/x' ; bad");
        assertTrue(args.containsAll(List.of("--context","test","--namespace","ns","-c","container")));
        assertEquals("/x' ; bad",args.getLast());assertFalse(args.contains("-t"));
    }
    @Test public void concurrentDirectoryTargetCannotReceiveMisplacedUpload() throws Exception {
        Path source=root.resolve("source");Files.writeString(source,"new");
        Path target=root.resolve("target");Files.writeString(target,"old");
        Files.writeString(root.resolve("race-directory"),"trigger");
        assertThrows(IOException.class,()->client.upload(source,target.toString(),true,Progress.NONE));
        assertTrue(Files.isDirectory(target));
        try(var children=Files.list(target)){assertEquals(0,children.count());}
        assertNoPartials();
    }
    @Test public void cancellationDuringTransferPreservesOriginal() throws Exception {
        Path source=root.resolve("source");Files.write(source,new byte[8*1024*1024]);
        Path target=root.resolve("target");Files.writeString(target,"old");
        java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        Progress progress=new Progress(){
            public void transferred(long n){if(n>0)cancelled.set(true);}
            public void checkCancelled(){if(cancelled.get())throw new CancellationException();}
        };
        assertThrows(CancellationException.class,()->client.upload(source,target.toString(),true,progress));
        assertEquals("old",Files.readString(target));assertNoPartials();
    }
    @Test public void corruptedUploadFailsChecksumAndPreservesOriginal() throws Exception {
        Path source=root.resolve("source");Files.writeString(source,"new");
        Path target=root.resolve("target");Files.writeString(target,"old");
        Path cat=root.resolve("bin/cat");
        Files.writeString(cat,"#!/bin/sh\nif [ \"$#\" = 0 ]; then /bin/cat; printf corrupted; else exec /bin/cat \"$@\"; fi\n");
        cat.toFile().setExecutable(true);
        IOException error=assertThrows(IOException.class,()->client.upload(source,target.toString(),true,Progress.NONE));
        assertTrue(error.getMessage().contains("SHA-256"));assertEquals("old",Files.readString(target));assertNoPartials();
    }
    private void assertNoPartials() throws IOException {
        try(var paths=Files.list(root)){assertFalse(paths.anyMatch(p->p.getFileName().toString().contains(".kfiles-")));}
    }
}
