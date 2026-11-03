package io.github.kubernetesfiles.core;
public interface Progress {
    Progress NONE = new Progress() {};
    default void checkCancelled() {}
    default void transferred(long bytes) {}
}
