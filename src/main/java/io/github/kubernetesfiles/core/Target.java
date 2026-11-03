package io.github.kubernetesfiles.core;

public record Target(String kubectl, String kubeconfig, String context, String namespace, String pod, String container) {
    public Target {
        if (kubectl == null || kubectl.isBlank()) throw new IllegalArgumentException("kubectl 路径不能为空");
        for (String value : new String[]{context, namespace, pod, container}) {
            if (value == null || value.isBlank() || value.startsWith("-")) throw new IllegalArgumentException("连接字段不能为空或以 - 开头");
        }
        kubeconfig = kubeconfig == null ? "" : kubeconfig;
    }
}
