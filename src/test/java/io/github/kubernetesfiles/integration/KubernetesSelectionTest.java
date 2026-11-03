package io.github.kubernetesfiles.integration;

import com.intellij.kubernetes.api.KubernetesEntity;
import com.intellij.kubernetes.api.config.Context;
import com.intellij.kubernetes.view.ui.actions.ActionUtilsKt;
import com.intellij.kubernetes.view.ui.actions.SelectedKubernetesResource;
import com.intellij.kubernetes.view.nodes.workloads.KubernetesContainerObjectImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.openapi.vfs.LocalFileSystem;
import io.kubernetes.client.openapi.models.*;
import java.util.List;
import java.util.Map;
import io.github.kubernetesfiles.core.Target;

public final class KubernetesSelectionTest extends LightPlatformTestCase {
    private AnActionEvent event(String... names) throws Exception {
        var file = java.nio.file.Files.createTempFile("kfiles-test-", ".config").toFile();
        file.deleteOnExit();
        var config = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        assertNotNull(config);
        var context = new Context(config.getUrl(), "ignored-namespace", "test-context",
                Map.of(), Map.of(), Map.of(), Map.of());
        var pod = new V1Pod().apiVersion("v1").kind("Pod").metadata(new V1ObjectMeta().name("test-pod").namespace("pod-namespace"))
                .spec(new V1PodSpec().containers(java.util.Arrays.stream(names)
                        .map(name -> new V1Container().name(name)).toList()));
        var data = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, getProject())
                .add(ActionUtilsKt.getKUBERNETES_CONTEXT(), context)
                .add(ActionUtilsKt.getKUBERNETES_RESOURCES(),
                        List.of(new SelectedKubernetesResource(KubernetesEntity.Pod, false, pod)))
                .build();
        return AnActionEvent.createFromDataContext("ServicesPopup", null, data);
    }

    public void testSingleContainerPodSelectionProvidesExactTarget() throws Exception {
        var event=event("app");
        var target = KubernetesSelection.selected(event, "/usr/bin/kubectl");
        assertNotNull("Selecting a Pod must resolve its only container", target);
        assertEquals("test-context", target.context());
        assertEquals("pod-namespace", target.namespace());
        assertEquals("test-pod", target.pod());
        assertEquals("app", target.container());
        assertTrue(target.kubeconfig().endsWith(".config"));
        assertTrue(java.nio.file.Path.of(target.kubeconfig()).isAbsolute());
    }

    public void testMultiContainerPodRequiresExplicitChoice() throws Exception {
        var event=event("app","sidecar");
        assertTrue(KubernetesSelection.hasSelection(event));
        assertNull("Never silently select the first container",KubernetesSelection.selected(event,"kubectl"));
        assertEquals(List.of("app","sidecar"),KubernetesSelection.targets(event,"kubectl")
                .stream().map(Target::container).toList());
    }

    public void testMultiContainerMenuShowsChildrenAndBindsChosenContainer() throws Exception {
        var event=event("app","sidecar");
        var chosen=new java.util.ArrayList<String>();
        var group=new OpenSelectedContainerAction((e,name)->chosen.add(name));
        group.update(event);
        assertTrue(event.getPresentation().isPopupGroup());
        assertFalse(event.getPresentation().isPerformGroup());
        var children=group.getChildren(event);
        assertEquals(2,children.length);
        assertEquals("app",children[0].getTemplatePresentation().getText());
        assertEquals("sidecar",children[1].getTemplatePresentation().getText());
        children[1].actionPerformed(event);
        assertEquals(List.of("sidecar"),chosen);
    }

    public void testSingleContainerMenuRunsDirectly() throws Exception {
        var event=event("app");
        var chosen=new java.util.ArrayList<String>();
        var group=new OpenSelectedContainerAction((e,name)->chosen.add(name));
        group.update(event);
        assertFalse(event.getPresentation().isPopupGroup());
        assertTrue(event.getPresentation().isPerformGroup());
        group.actionPerformed(event);
        assertEquals(List.of("app"),chosen);
    }

    public void testUnrelatedServicesHideAction() {
        var event=AnActionEvent.createFromDataContext("ServicesPopup",null,
                SimpleDataContext.builder().add(CommonDataKeys.PROJECT,getProject()).build());
        var action=new OpenSelectedContainerAction();
        action.update(event);
        assertFalse(event.getPresentation().isVisible());
        assertFalse(event.getPresentation().isEnabled());
    }

    public void testPodPopupActionIsEnabled() throws Exception {
        var event=event("app");
        new OpenSelectedContainerAction().update(event);
        assertTrue(event.getPresentation().isVisible());
        assertTrue(event.getPresentation().isEnabled());
    }

    public void testMultiSelectedPodsAreNotResolvedToOneTarget() throws Exception {
        var original=event("app");
        var data=SimpleDataContext.builder().setParent(original.getDataContext())
                .add(PlatformCoreDataKeys.SELECTED_ITEMS,new Object[]{"pod1","pod2"}).build();
        var multiple=AnActionEvent.createFromDataContext("ServicesPopup",null,data);
        assertFalse(KubernetesSelection.hasSelection(multiple));
        assertTrue(KubernetesSelection.targets(multiple,"kubectl").isEmpty());
    }

    public void testContainerSelectionOverridesPodContainerList() throws Exception {
        var original=event("app","sidecar");
        var context=original.getData(ActionUtilsKt.getKUBERNETES_CONTEXT());
        var pod=(V1Pod)original.getData(ActionUtilsKt.getKUBERNETES_RESOURCES()).getFirst().getSelectedValue();
        var container=new KubernetesContainerObjectImpl(pod.getSpec().getContainers().get(1),
                new V1ContainerStatus().name("sidecar"),pod,context);
        var data=SimpleDataContext.builder().setParent(original.getDataContext())
                .add(ActionUtilsKt.getKUBERNETES_CONTAINER(),container).build();
        var selected=AnActionEvent.createFromDataContext("ServicesPopup",null,data);
        assertEquals("sidecar",KubernetesSelection.selected(selected,"kubectl").container());
    }

    public void testNonLocalConfigNeverFallsBackToDefault() throws Exception {
        var original=event("app");
        var context=new Context("memory://missing-config","default","test-context",
                Map.of(),Map.of(),Map.of(),Map.of());
        var data=SimpleDataContext.builder().setParent(original.getDataContext())
                .add(ActionUtilsKt.getKUBERNETES_CONTEXT(),context).build();
        var selected=AnActionEvent.createFromDataContext("ServicesPopup",null,data);
        try {
            KubernetesSelection.targets(selected,"kubectl");
            fail("Missing/non-local kubeconfig must be rejected");
        } catch(IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("kubeconfig"));
        }
    }
}
