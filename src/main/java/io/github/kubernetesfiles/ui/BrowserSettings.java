package io.github.kubernetesfiles.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
@State(name="KubernetesFilesSettings",storages=@Storage("kubernetes-files.xml"))
public final class BrowserSettings implements PersistentStateComponent<BrowserSettings.SettingsState> {
    public static final class SettingsState {
        public String defaultRemoteDirectory="";
    }
    private volatile SettingsState state=new SettingsState();

    public static BrowserSettings getInstance(){
        return ApplicationManager.getApplication().getService(BrowserSettings.class);
    }
    public String defaultDirectory(){return state.defaultRemoteDirectory==null?"":state.defaultRemoteDirectory;}
    public void setDefaultDirectory(String value){
        String directory=value==null || value.isBlank()?"":value;
        if(!directory.isEmpty() && (!directory.startsWith("/") || directory.indexOf(0)>=0))
            throw new IllegalArgumentException("默认远程目录必须是绝对路径（例如 /data1/weibo/bin），或留空以自动识别");
        var next=new SettingsState();next.defaultRemoteDirectory=directory;state=next;
    }
    @Override public @NotNull SettingsState getState(){return state;}
    @Override public void loadState(@NotNull SettingsState value){setDefaultDirectory(value.defaultRemoteDirectory);}
}
