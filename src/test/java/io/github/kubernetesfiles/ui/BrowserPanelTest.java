package io.github.kubernetesfiles.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.testFramework.LightPlatformTestCase;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Loads the real plugin in the IDE test application, rather than mocking IntelliJ APIs. */
public final class BrowserPanelTest extends LightPlatformTestCase {
    public void testNativeFileBrowserHasNoStandaloneWindowOrManualConnectionEntry() {
        assertNull("Standalone action must no longer be registered",ActionManager.getInstance().getAction("KubernetesFiles.OpenBrowser"));
        BrowserPanel panel=new BrowserPanel(getProject());
        com.intellij.openapi.util.Disposer.register(getTestRootDisposable(),panel);
        List<String> buttons=new ArrayList<>();collectButtons(panel,buttons);
        assertTrue(buttons.contains("上传文件…"));
        assertTrue(buttons.contains("下载选中文件…"));
        assertTrue(buttons.contains("取消"));
        assertTrue(buttons.contains("重新连接"));
        assertFalse(buttons.contains("连接 / 浏览"));
        assertFalse(buttons.contains("读取 Context"));
        assertFalse(buttons.contains("读取 Pod / Container"));
        try(var descriptor=getClass().getResourceAsStream("/META-INF/plugin.xml")){
            assertNotNull(descriptor);
            var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();
            var document=factory.newDocumentBuilder().parse(descriptor);
            assertEquals("No independent tool window may be declared",0,document.getElementsByTagName("toolWindow").getLength());
        }catch(Exception e){throw new AssertionError(e);}
        panel.setSize(1100,800);layout(panel);
        var screenshot=new java.awt.image.BufferedImage(1100,800,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=screenshot.createGraphics();panel.paint(graphics);graphics.dispose();
        try {
            var file=java.nio.file.Path.of("build/reports/ui/browser-panel.png");
            java.nio.file.Files.createDirectories(file.getParent());
            javax.imageio.ImageIO.write(screenshot,"png",file.toFile());
        }catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static void layout(Container parent){
        parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container container)layout(container);
    }
    private static void collectButtons(Container parent,List<String> labels){
        for(Component child:parent.getComponents()) {
            if(child instanceof JButton button) labels.add(button.getText());
            if(child instanceof Container container)collectButtons(container,labels);
        }
    }
}
