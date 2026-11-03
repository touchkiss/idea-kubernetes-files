package io.github.kubernetesfiles.integration;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import io.github.kubernetesfiles.ui.BrowserPanel;
import org.jetbrains.annotations.NotNull;

public final class OpenSelectedContainerAction extends ActionGroup implements DumbAware {
    private final java.util.function.BiConsumer<AnActionEvent,String> opener;
    public OpenSelectedContainerAction(){this(OpenSelectedContainerAction::open);}
    OpenSelectedContainerAction(java.util.function.BiConsumer<AnActionEvent,String> opener){this.opener=opener;}
    @Override public @NotNull ActionUpdateThread getActionUpdateThread(){return ActionUpdateThread.BGT;}
    @Override public void update(@NotNull AnActionEvent e){
        int count=e.getProject()==null ? 0 : KubernetesSelection.containerNames(e).size();
        boolean selected=count>0;
        e.getPresentation().setEnabled(selected);
        e.getPresentation().setVisible(selected || !e.getPlace().startsWith("Services"));
        // Same presentation contract as KubernetesSmartPopupActionGroup: a single
        // container is directly executable; multiple containers form a submenu.
        e.getPresentation().setPerformGroup(count==1);
        e.getPresentation().setPopupGroup(count>1 || (count==1 && ActionPlaces.KEYBOARD_SHORTCUT.equals(e.getPlace())));
    }
    @Override public AnAction @NotNull [] getChildren(AnActionEvent e){
        if(e==null || e.getProject()==null)return EMPTY_ARRAY;
        var names=KubernetesSelection.containerNames(e);
        return names.stream().map(name->new DumbAwareAction(names.size()==1?"浏览容器文件…":name,
                "浏览容器 "+name+" 的文件",com.intellij.icons.AllIcons.Nodes.Folder){
            @Override public void actionPerformed(@NotNull AnActionEvent event){opener.accept(event,name);}
        }).toArray(AnAction[]::new);
    }
    @Override public void actionPerformed(@NotNull AnActionEvent e){
        var names=KubernetesSelection.containerNames(e);
        if(names.size()==1)opener.accept(e,names.getFirst());
    }
    private static void open(AnActionEvent e,String containerName){
        if(e.getProject()==null)return;
        try{
            var owner=NativeFilesTabs.owner(e);
            if(owner==null)throw new IllegalArgumentException("请从 Services / Kubernetes 中的 Pod 或容器节点打开 Files 页签");
            var targets=KubernetesSelection.targets(e,BrowserPanel.detectKubectl());
            if(targets.isEmpty()) {
                Messages.showInfoMessage(e.getProject(),"请在 Services / Kubernetes 中选择一个 Pod 或容器节点。","Kubernetes Files");
                return;
            }
            var target=targets.stream().filter(t->t.container().equals(containerName)).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("所选容器已不可用，请重新打开右键菜单"));
            e.getProject().getService(NativeFilesTabs.class).open(owner,target);
        }catch(IllegalArgumentException ex){Messages.showErrorDialog(e.getProject(),ex.getMessage(),"Kubernetes Files");}
    }
}
