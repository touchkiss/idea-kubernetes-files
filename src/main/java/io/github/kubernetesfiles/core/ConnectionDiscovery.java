package io.github.kubernetesfiles.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class ConnectionDiscovery {
    public record Pod(String name, String phase, List<String> containers) {
        @Override public String toString() { return name + " (" + phase + ")"; }
    }
    private ConnectionDiscovery() {}
    public static List<String> contexts(String executable,String config,Progress progress) throws IOException {
        var args=KubectlClient.baseCommand(executable,config);
        args.addAll(List.of("config","get-contexts","-o","name"));
        return new String(new ProcessRunner().run(args,null,null,progress,Duration.ofSeconds(30)),StandardCharsets.UTF_8)
                .lines().filter(s->!s.isBlank()).toList();
    }
    public static List<Pod> pods(String executable,String config,String context,String namespace,Progress progress) throws IOException {
        if(context.isBlank() || namespace.isBlank() || context.startsWith("-") || namespace.startsWith("-")) throw new IOException("请选择 context 和 namespace");
        var args=KubectlClient.baseCommand(executable,config);
        args.addAll(List.of("--context",context,"--namespace",namespace,"get","pods","-o","json"));
        String json=new String(new ProcessRunner().run(args,null,null,progress,Duration.ofSeconds(60)),StandardCharsets.UTF_8);
        return parsePods(json);
    }
    static List<Pod> parsePods(String json) throws IOException {
        try {
            List<Pod> result=new ArrayList<>();
            for(JsonElement element:JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("items")) {
                JsonObject pod=element.getAsJsonObject();
                String name=pod.getAsJsonObject("metadata").get("name").getAsString();
                String phase=pod.has("status") && pod.getAsJsonObject("status").has("phase") ? pod.getAsJsonObject("status").get("phase").getAsString() : "Unknown";
                List<String> containers=new ArrayList<>();
                for(JsonElement c:pod.getAsJsonObject("spec").getAsJsonArray("containers")) containers.add(c.getAsJsonObject().get("name").getAsString());
                result.add(new Pod(name,phase,List.copyOf(containers)));
            }
            result.sort(Comparator.comparing(Pod::name)); return result;
        } catch(RuntimeException e) {throw new IOException("无法解析 Pod 列表",e);}
    }
}
