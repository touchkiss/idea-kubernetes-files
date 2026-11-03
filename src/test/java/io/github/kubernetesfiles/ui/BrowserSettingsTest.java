package io.github.kubernetesfiles.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import javax.swing.*;
import java.awt.*;

public final class BrowserSettingsTest extends LightPlatformTestCase {
    public void testDefaultIsAutomaticAndFixedDirectorySurvivesSerialization() {
        var settings=new BrowserSettings();
        assertEquals("",settings.defaultDirectory());
        settings.setDefaultDirectory("/data1/weibo/bin");
        var saved=XmlSerializer.serialize(settings.getState());
        var restored=new BrowserSettings();
        restored.loadState(XmlSerializer.deserialize(saved,BrowserSettings.SettingsState.class));
        assertEquals("/data1/weibo/bin",restored.defaultDirectory());
    }
    public void testSettingsUiAppliesResetsAndRejectsRelativeDirectory() throws Exception {
        var settings=BrowserSettings.getInstance();String previous=settings.defaultDirectory();
        var ui=new BrowserSettingsConfigurable();
        try{
            var component=ui.createComponent();var field=field(component);assertNotNull(field);
            field.setText("/data1/weibo/bin");ui.apply();
            render(component);
            assertEquals("/data1/weibo/bin",settings.defaultDirectory());
            field.setText("relative");assertTrue(ui.isModified());
            try{ui.apply();fail("Relative paths must be rejected");}catch(ConfigurationException expected){}
            assertEquals("/data1/weibo/bin",settings.defaultDirectory());
            ui.reset();assertEquals("/data1/weibo/bin",field.getText());assertFalse(ui.isModified());
            field.setText("");ui.apply();assertEquals("",settings.defaultDirectory());
        }finally{settings.setDefaultDirectory(previous);ui.disposeUIResources();}
    }
    public void testNewPanelsUseDefaultWithoutChangingAnExistingPanel() {
        var settings=BrowserSettings.getInstance();String previous=settings.defaultDirectory();
        try{
            settings.setDefaultDirectory("/data1/weibo/bin");
            var first=new BrowserPanel(getProject());
            com.intellij.openapi.util.Disposer.register(getTestRootDisposable(),first);
            assertEquals("/data1/weibo/bin",remotePath(first).getText());
            settings.setDefaultDirectory("");
            var second=new BrowserPanel(getProject());
            com.intellij.openapi.util.Disposer.register(getTestRootDisposable(),second);
            assertEquals("",remotePath(second).getText());
            assertEquals("/data1/weibo/bin",remotePath(first).getText());
        }finally{settings.setDefaultDirectory(previous);}
    }
    private static JTextField remotePath(Container parent){
        for(Component child:parent.getComponents()){
            if(child instanceof JTextField text && text.getToolTipText()!=null
                    && text.getToolTipText().contains("自动识别工作目录"))return text;
            if(child instanceof Container container){var found=remotePath(container);if(found!=null)return found;}
        }
        return null;
    }
    private static void render(JComponent component)throws Exception{
        component.setSize(750,220);layout(component);
        var image=new java.awt.image.BufferedImage(750,220,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();component.paint(graphics);graphics.dispose();
        var file=java.nio.file.Path.of("build/reports/ui/browser-settings.png");
        java.nio.file.Files.createDirectories(file.getParent());
        javax.imageio.ImageIO.write(image,"png",file.toFile());
    }
    private static void layout(Container parent){
        parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container container)layout(container);
    }
    private static JTextField field(Container parent){
        for(Component child:parent.getComponents()){
            if(child instanceof JTextField text)return text;
            if(child instanceof Container container){var found=field(container);if(found!=null)return found;}
        }
        return null;
    }
}
