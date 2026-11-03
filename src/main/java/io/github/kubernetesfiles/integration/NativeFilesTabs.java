package io.github.kubernetesfiles.integration;

import com.intellij.kubernetes.view.nodes.workloads.KubernetesContainerServiceViewDescriptor;
import com.intellij.kubernetes.view.nodes.workloads.KubernetesPodServiceViewContributorDescriptor;
import com.intellij.kubernetes.view.ui.KubernetesResourceContent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.content.Content;
import io.github.kubernetesfiles.core.Target;
import io.github.kubernetesfiles.ui.BrowserPanel;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Version-sensitive native content API is intentionally confined to the 262 adapter. */
@Service(Service.Level.PROJECT)
public final class NativeFilesTabs implements Disposable {
    private static final Key<Target> TARGET=Key.create("kubernetesFiles.nativeTarget");
    private final Project project;
    private final Set<Content> attached=new HashSet<>();

    public NativeFilesTabs(Project project){this.project=project;}

    public static KubernetesResourceContent<?> owner(AnActionEvent event) {
        var item=event.getData(PlatformCoreDataKeys.SELECTED_ITEM);
        if(item instanceof KubernetesContainerServiceViewDescriptor container) return container.getContent();
        if(item instanceof KubernetesPodServiceViewContributorDescriptor pod) return pod.getContent();
        return null;
    }

    public Content open(KubernetesResourceContent<?> owner,Target target) {
        return open(owner,target,panel->panel.setTarget(target));
    }

    // Tests use the real native content manager with a connector that never invokes kubectl.
    Content open(KubernetesResourceContent<?> owner,Target target,Consumer<BrowserPanel> connect) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        var manager=owner.getManager$intellij_clouds_kubernetes();
        for(var content:manager.getContents()) {
            if(target.equals(content.getUserData(TARGET))) {
                manager.setSelectedContent(content,true);
                return content;
            }
        }
        var panel=new BrowserPanel(project);
        var content=owner.addConsoleTab(panel,"Files: "+target.container());
        content.putUserData(TARGET,target);
        content.setDisposer(panel);
        attached.add(content);
        Disposer.register(content,()->attached.remove(content));
        try {
            connect.accept(panel);
            return content;
        } catch(RuntimeException error) {
            manager.removeContent(content,true);
            throw error;
        }
    }

    @Override public void dispose() {
        Runnable cleanup=()->{
            for(var content:Set.copyOf(attached)) {
                var manager=content.getManager();
                if(manager!=null && !manager.isDisposed()) manager.removeContent(content,true);
                else if(!Disposer.isDisposed(content)) Disposer.dispose(content);
            }
            attached.clear();
        };
        var app=ApplicationManager.getApplication();
        if(app.isDispatchThread()) cleanup.run();
        else app.invokeAndWait(cleanup);
    }
}
