package io.github.kubernetesfiles.integration;

import com.intellij.kubernetes.api.KubernetesApiModelInterface;
import com.intellij.kubernetes.view.ui.KubernetesResourceContent;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import io.github.kubernetesfiles.core.Target;
import io.kubernetes.client.openapi.models.V1Pod;
import javax.swing.*;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.awt.Component;
import java.awt.Container;

public final class NativeFilesTabsTest extends LightPlatformTestCase {
    private KubernetesResourceContent<V1Pod> owner() {
        var api=(KubernetesApiModelInterface) Proxy.newProxyInstance(
                KubernetesApiModelInterface.class.getClassLoader(),new Class[]{KubernetesApiModelInterface.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "getProject" -> getProject();
                    case "toString" -> "Synthetic Kubernetes model (no network)";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy==args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new KubernetesResourceContent<>(new V1Pod(),getTestRootDisposable(),api,model->List.of(),null);
    }
    private NativeFilesTabs tabs() {
        var tabs=new NativeFilesTabs(getProject());
        Disposer.register(getTestRootDisposable(),tabs);
        return tabs;
    }
    private Target target(String container) {
        return new Target("kubectl","/synthetic/config","ctx","ns","pod",container);
    }
    public void testFilesAppearsBesideExistingNativeTabsAndIsSelected() throws Exception {
        var owner=owner();
        var shell=owner.addConsoleTab(new JPanel(),"Shell: app");
        var calls=new AtomicInteger();
        var files=tabs().open(owner,target("app"),panel->calls.incrementAndGet());
        var manager=owner.getManager$intellij_clouds_kubernetes();
        assertEquals(3,manager.getContentCount());
        assertEquals("Files: app",files.getDisplayName());
        assertSame(files,manager.getSelectedContent());
        assertTrue(List.of(manager.getContents()).contains(shell));
        assertEquals(1,calls.get());
        assertTrue("Embedded files must allow retrying the initial connection",hasVisibleButton(files.getComponent(),"重新连接"));
        render(manager.getComponent());
    }
    public void testReopeningSameTargetKeepsExistingPanelAndDoesNotReconnect() {
        var owner=owner(); var tabs=tabs(); var calls=new AtomicInteger();
        var first=tabs.open(owner,target("app"),panel->calls.incrementAndGet());
        var second=tabs.open(owner,target("app"),panel->calls.incrementAndGet());
        assertSame(first,second);
        assertEquals(2,owner.getManager$intellij_clouds_kubernetes().getContentCount());
        assertEquals(1,calls.get());
    }
    public void testDifferentContainersKeepSeparateTabsAndClosingAllowsReopen() {
        var owner=owner(); var tabs=tabs();
        var first=tabs.open(owner,target("app"),panel->{});
        var second=tabs.open(owner,target("sidecar"),panel->{});
        assertNotSame(first.getComponent(),second.getComponent());
        assertTrue(owner.getManager$intellij_clouds_kubernetes().removeContent(first,true));
        assertTrue(Disposer.isDisposed(first));
        assertTrue(Disposer.isDisposed((io.github.kubernetesfiles.ui.BrowserPanel)first.getComponent()));
        var reopened=tabs.open(owner,target("app"),panel->{});
        assertNotSame(first,reopened);
    }
    public void testPluginDisposalRemovesOnlyFilesTabs() {
        var owner=owner();var tabs=tabs();
        var shell=owner.addConsoleTab(new JPanel(),"Shell: app");
        var files=tabs.open(owner,target("app"),panel->{});
        Disposer.dispose(tabs);
        assertTrue(Disposer.isDisposed(files));
        assertFalse(Disposer.isDisposed(shell));
        assertEquals(2,owner.getManager$intellij_clouds_kubernetes().getContentCount());
    }

    public void testClosingNativeOwnerDisposesFilesPanel() {
        var owner=owner();
        var files=tabs().open(owner,target("app"),panel->{});
        var panel=(io.github.kubernetesfiles.ui.BrowserPanel)files.getComponent();
        Disposer.dispose(owner);
        assertTrue(Disposer.isDisposed(panel));
    }

    private static void render(JComponent component) throws Exception {
        component.setSize(1200,800); layout(component);
        var image=new java.awt.image.BufferedImage(1200,800,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();component.paint(graphics);graphics.dispose();
        var file=java.nio.file.Path.of("build/reports/ui/native-files-tab.png");
        java.nio.file.Files.createDirectories(file.getParent());
        javax.imageio.ImageIO.write(image,"png",file.toFile());
    }
    private static void layout(Container parent) {
        parent.doLayout();
        for(Component child:parent.getComponents()) if(child instanceof Container container) layout(container);
    }
    private static boolean hasVisibleButton(Component component,String text) {
        if(!component.isVisible()) return false;
        if(component instanceof JButton button && text.equals(button.getText())) return true;
        if(component instanceof Container container)
            for(Component child:container.getComponents()) if(hasVisibleButton(child,text)) return true;
        return false;
    }
}
