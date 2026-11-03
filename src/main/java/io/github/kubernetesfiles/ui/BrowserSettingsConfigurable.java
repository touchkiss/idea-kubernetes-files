package io.github.kubernetesfiles.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBTextField;
import javax.swing.*;
import java.awt.*;

public final class BrowserSettingsConfigurable implements Configurable {
    private JBTextField directory;
    @Override public String getDisplayName(){return "Kubernetes Files";}
    @Override public JComponent createComponent(){
        directory=new JBTextField();directory.getEmptyText().setText("自动识别容器工作目录");
        var field=new JPanel(new BorderLayout(8,4));
        field.add(new JLabel("默认远程目录："),BorderLayout.WEST);field.add(directory,BorderLayout.CENTER);
        var help=new JLabel("<html>填写绝对路径，例如 /data1/weibo/bin；留空时通过非交互 pwd -P 自动识别。<br>"
                +"自动识别失败时回退到 /；固定路径不可用时直接报错。<br>"
                +"新建连接时生效，已有 Files 页签保持当前目录。不会跟随 Shell 内的 cd。</html>");
        var top=new JPanel(new BorderLayout(8,12));top.add(field,BorderLayout.NORTH);top.add(help,BorderLayout.CENTER);
        var panel=new JPanel(new BorderLayout());panel.add(top,BorderLayout.NORTH);reset();return panel;
    }
    @Override public boolean isModified(){
        return directory!=null && !normalized().equals(BrowserSettings.getInstance().defaultDirectory());
    }
    private String normalized(){return directory.getText().isBlank()?"":directory.getText();}
    @Override public void apply()throws ConfigurationException{
        if(directory==null)return;
        try{BrowserSettings.getInstance().setDefaultDirectory(normalized());}
        catch(IllegalArgumentException e){throw new ConfigurationException(e.getMessage());}
    }
    @Override public void reset(){if(directory!=null)directory.setText(BrowserSettings.getInstance().defaultDirectory());}
    @Override public void disposeUIResources(){directory=null;}
}
