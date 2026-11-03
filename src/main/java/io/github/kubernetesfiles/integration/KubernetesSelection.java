package io.github.kubernetesfiles.integration;

import com.intellij.kubernetes.view.ui.actions.ActionUtilsKt;
import com.intellij.kubernetes.api.config.Context;
import com.intellij.kubernetes.view.nodes.workloads.KubernetesContainerServiceViewDescriptor;
import com.intellij.kubernetes.view.nodes.workloads.KubernetesPodServiceViewContributorDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import io.github.kubernetesfiles.core.Target;
import io.kubernetes.client.openapi.models.V1Pod;
import java.nio.file.Path;
import java.util.List;

/** All version-sensitive Kubernetes plugin access lives in this adapter (262 only). */
public final class KubernetesSelection {
    private KubernetesSelection() {}
    public static Target selected(AnActionEvent event,String kubectl) {
        var targets=targets(event,kubectl);
        return targets.size()==1 ? targets.getFirst() : null;
    }

    public static boolean hasSelection(AnActionEvent event) {
        return selection(event)!=null;
    }

    public static List<String> containerNames(AnActionEvent event) {
        var selection=selection(event);
        return selection==null ? List.of() : selection.containers();
    }

    public static List<Target> targets(AnActionEvent event,String kubectl) {
        var selection=selection(event);
        if(selection==null) return List.of();
        var metadata=selection.pod().getMetadata();
        if(metadata==null || metadata.getName()==null || metadata.getNamespace()==null)
            throw new IllegalArgumentException("无法读取 Pod 名称或 namespace，请刷新原生 Kubernetes 节点后重试");
        var config=selection.context().getKubeConfigFile();
        // Never fall back to the default kubeconfig for an in-memory/non-local config.
        if(config==null || !config.isInLocalFileSystem()) throw new IllegalArgumentException("此容器使用的 kubeconfig 不是本地文件，请在文件面板指定本地 kubeconfig");
        String configPath=Path.of(config.getPath()).toString();
        return selection.containers().stream().map(name->new Target(kubectl,configPath,
                selection.context().getName(),metadata.getNamespace(),metadata.getName(),name)).toList();
    }

    private record Selection(Context context,V1Pod pod,List<String> containers) {}

    private static Selection selection(AnActionEvent event) {
        var items=event.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
        if(items!=null && items.length>1) return null;
        var container=event.getData(ActionUtilsKt.getKUBERNETES_CONTAINER());
        var item=event.getData(PlatformCoreDataKeys.SELECTED_ITEM);
        if(container==null && item instanceof KubernetesContainerServiceViewDescriptor descriptor)
            container=descriptor.getResource();
        if(container!=null)
            return new Selection(container.getContext(),container.getParentPod(),List.of(container.getName()));

        Context context=event.getData(ActionUtilsKt.getKUBERNETES_CONTEXT());
        V1Pod pod=null;
        if(item instanceof KubernetesPodServiceViewContributorDescriptor descriptor) {
            pod=descriptor.getResource();
            context=descriptor.getApiModel().getCurrentContextObject();
        } else {
            var resources=event.getData(ActionUtilsKt.getKUBERNETES_RESOURCES());
            if(resources!=null && resources.size()==1 && !resources.getFirst().getSelectedAll()
                    && resources.getFirst().getSelectedValue() instanceof V1Pod selectedPod) pod=selectedPod;
        }
        if(context==null || pod==null || pod.getSpec()==null || pod.getSpec().getContainers()==null) return null;
        var names=pod.getSpec().getContainers().stream().map(c->c.getName())
                .filter(name->name!=null && !name.isBlank()).distinct().toList();
        return names.isEmpty() ? null : new Selection(context,pod,names);
    }
}
