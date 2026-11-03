package io.github.kubernetesfiles.core;
public record RemoteEntry(String name, String type, long size) {
    public boolean directory() { return type.equals("d"); }
    public boolean regularFile() { return type.equals("f"); }
}
