package io.github.kubernetesfiles.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.*;
import com.intellij.ui.table.JBTable;
import io.github.kubernetesfiles.core.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.io.File;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static io.github.kubernetesfiles.ui.TransferProgress.human;

public final class BrowserPanel extends JPanel implements Disposable {
    private final Project project;
    private final JBTextField path=new JBTextField();
    private final JLabel connection=new JLabel("正在连接选中的 Kubernetes 容器…");
    private final JLabel status=new JLabel("就绪 · 支持普通文件上传/下载；拖文件到列表上传");
    private final JProgressBar progressBar=new JProgressBar(0,1000);
    private final JTextArea history=new JTextArea(5,50);
    private final EntriesModel model=new EntriesModel();
    private final JBTable table=new JBTable(model);
    private final List<JComponent> controls=new ArrayList<>();
    private final JButton cancel=new JButton("取消");
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile boolean disposed;
    private boolean busy;
    private long operation;
    private Target target;
    private Target connected;
    private String directory="/";
    public BrowserPanel(Project project){
        super(new BorderLayout(6,6));this.project=project;
        path.setText(BrowserSettings.getInstance().defaultDirectory());
        path.getEmptyText().setText("自动识别容器工作目录");
        path.setToolTipText("连接时留空以自动识别工作目录；默认值可在 Settings → Tools → Kubernetes Files 设置");
        setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        JPanel north=new JPanel(new BorderLayout(6,8));north.add(connection,BorderLayout.NORTH);
        JPanel navigation=new JPanel(new BorderLayout(4,4));controls.add(path);navigation.add(path,BorderLayout.CENTER);
        JPanel commands=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));
        commands.add(button("重新连接",this::connect));
        commands.add(button("上级",this::up));commands.add(button("刷新",this::refresh));
        commands.add(button("上传文件…",this::chooseUpload));commands.add(button("下载选中文件…",this::chooseDownload));
        JPanel navTop=new JPanel(new BorderLayout());navTop.add(new JLabel("远程路径  "),BorderLayout.WEST);navTop.add(navigation,BorderLayout.CENTER);
        JPanel nav=new JPanel(new BorderLayout(4,4));nav.add(navTop,BorderLayout.NORTH);nav.add(commands,BorderLayout.SOUTH);
        north.add(nav,BorderLayout.SOUTH);add(north,BorderLayout.NORTH);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2 && !busy) enterSelected();}});
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"enter-directory");
        table.getActionMap().put("enter-directory",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){if(!busy) enterSelected();}});
        table.setTransferHandler(new TransferHandler(){
            @Override public boolean canImport(TransferSupport support){return !busy && connected!=null && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);}
            @Override public boolean importData(TransferSupport support){
                if(!canImport(support))return false;
                try{
                    @SuppressWarnings("unchecked") List<File> files=(List<File>)support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    upload(files.stream().map(File::toPath).toList());return true;
                }catch(Exception e){error(e);return false;}
            }
        });
        history.setEditable(false);history.setLineWrap(true);history.setWrapStyleWord(true);
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JBScrollPane(table),new JBScrollPane(history));split.setResizeWeight(.85);
        add(split,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(4,4));bottom.add(status,BorderLayout.NORTH);bottom.add(progressBar,BorderLayout.CENTER);
        cancel.setEnabled(false);cancel.addActionListener(e->{cancelled.set(true);status.setText("正在取消并清理临时文件…");});bottom.add(cancel,BorderLayout.EAST);add(bottom,BorderLayout.SOUTH);
        path.addActionListener(e->refresh());
    }
    private JButton button(String label,Runnable action){JButton b=new JButton(label);b.addActionListener(e->action.run());controls.add(b);return b;}
    public void setTarget(Target target){
        if(busy){Messages.showInfoMessage(project,"当前有操作正在执行，请完成或取消后再切换容器。","Kubernetes Files");return;}
        this.target=target;connected=null;model.set(List.of());
        connection.setText(target.context()+" / "+target.namespace()+" / "+target.pod()+" / "+target.container());
        path.setText(BrowserSettings.getInstance().defaultDirectory());connect();
    }
    private void connect(){
        if(busy)return;
        if(target==null){Messages.showInfoMessage(project,"请从 Kubernetes 的 Pod 或容器节点打开 Files 页签。","Kubernetes Files");return;}
        try{Target target=this.target;String requested=path.getText();
            execute("连接并浏览容器目录",p->new KubectlClient(target).openInitialDirectory(requested,p),result->{
                connected=target;directory=result.directory();path.setText(directory);model.set(result.entries());
                connection.setText(target.context()+" / "+target.namespace()+" / "+target.pod()+" / "+target.container());
                if(!result.notice().isEmpty())log(result.notice());
                log("已连接："+connection.getText());
            });
        }catch(Exception e){error(e);}
    }
    private boolean requireConnection(){if(connected!=null)return true;Messages.showInfoMessage(project,"请先连接容器。","Kubernetes Files");return false;}
    private void refresh(){
        if(busy)return;
        if(connected==null || path.getText().isBlank()){connect();return;}
        Target target=connected;String requested=path.getText();
        execute("读取远程目录",p->new KubectlClient(target).list(requested,p),entries->{directory=requested;model.set(entries);});
    }
    private void up(){if(!requireConnection())return;String d=directory.replaceAll("/+$","");int slash=d.lastIndexOf('/');path.setText(slash<=0?"/":d.substring(0,slash));refresh();}
    private List<RemoteEntry> selected(){return Arrays.stream(table.getSelectedRows()).map(table::convertRowIndexToModel).mapToObj(model.entries::get).toList();}
    private void enterSelected(){var rows=selected();if(rows.size()==1 && rows.getFirst().directory()){path.setText(KubectlClient.join(directory,rows.getFirst().name()));refresh();}}
    private JFileChooser chooser(){JFileChooser c=new JFileChooser();if(project.getBasePath()!=null)c.setCurrentDirectory(new File(project.getBasePath()));return c;}
    private void chooseUpload(){if(!requireConnection())return;JFileChooser c=chooser();c.setMultiSelectionEnabled(true);c.setFileSelectionMode(JFileChooser.FILES_ONLY);if(c.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)upload(Arrays.stream(c.getSelectedFiles()).map(File::toPath).toList());}
    private void upload(List<Path> files){
        if(busy || !requireConnection() || files.isEmpty())return;
        if(files.stream().anyMatch(f->!Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS))){Messages.showErrorDialog(project,"首版只支持普通文件上传，不支持文件夹和符号链接。","Kubernetes Files");return;}
        Target target=connected;String dir=directory;
        execute("上传文件到 "+target.pod()+" / "+target.container(),p->{
            KubectlClient client=new KubectlClient(target);int completed=0;
            for(Path file:files){
                p.checkCancelled();String remote=KubectlClient.join(dir,file.getFileName().toString());
                boolean replace=client.exists(remote,p);if(replace && !confirm("远程文件已存在，是否覆盖？\n"+remote))continue;
                p.file(file.getFileName().toString(),Files.size(file));client.upload(file,remote,replace,p);completed++;
            }
            return completed;
        },count->{log("上传完成 "+count+" 个文件（SHA-256 已校验）");refresh();});
    }
    private void chooseDownload(){
        if(busy || !requireConnection())return;List<RemoteEntry> rows=selected();
        if(rows.isEmpty() || rows.stream().anyMatch(e->!e.regularFile())){Messages.showInfoMessage(project,"请选择一个或多个普通文件；文件夹与符号链接不支持下载。","Kubernetes Files");return;}
        JFileChooser c=chooser();
        if(rows.size()==1)c.setSelectedFile(new File(c.getCurrentDirectory(),rows.getFirst().name()));else c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path chosen=c.getSelectedFile().toPath();Target target=connected;String dir=directory;
        execute("下载文件："+target.pod()+" / "+target.container(),p->{KubectlClient client=new KubectlClient(target);int completed=0;
            for(RemoteEntry entry:rows){
                p.checkCancelled();Path local=rows.size()==1?chosen:chosen.resolve(entry.name());
                boolean replace=Files.exists(local,LinkOption.NOFOLLOW_LINKS);
                if(replace && !confirm("本地文件已存在，是否覆盖？\n"+local))continue;
                String remote=KubectlClient.join(dir,entry.name());p.file(entry.name(),client.size(remote,p));
                client.download(remote,local,replace,p);completed++;
            }
            return completed;
        },count->log("下载完成 "+count+" 个文件（SHA-256 已校验）："+chosen));
    }
    private boolean confirm(String message) throws Exception {
        final int[] answer={Messages.CANCEL};
        SwingUtilities.invokeAndWait(()->answer[0]=disposed?Messages.CANCEL:Messages.showYesNoCancelDialog(project,message,"覆盖确认",Messages.getQuestionIcon()));
        if(answer[0]==Messages.CANCEL)throw new ProcessCanceledException();return answer[0]==Messages.YES;
    }
    @FunctionalInterface private interface Work<T>{T run(TransferProgress progress) throws Exception;}
    private <T> void execute(String title,Work<T> work,java.util.function.Consumer<T> success){
        if(busy || disposed)return;operation++;busy=true;cancelled.set(false);controls.forEach(c->c.setEnabled(false));table.setEnabled(false);cancel.setEnabled(true);progressBar.setIndeterminate(true);status.setText(title);
        new Task.Backgroundable(project,title,true){
            private T result;
            @Override public boolean shouldStartInBackground(){return true;}
            @Override public void run(@NotNull ProgressIndicator indicator){
                final long generation=operation;
                indicator.setIndeterminate(true);indicator.setText(title);
                var progress=new TransferProgress(indicator,
                        ()->cancelled.get() || disposed || project.isDisposed(),
                        update->ApplicationManager.getApplication().invokeLater(()->{
                            if(!disposed && busy && operation==generation){
                                status.setText(update.text());progressBar.setIndeterminate(false);
                                progressBar.setValue((int)(update.fraction()*1000));
                            }
                        }),System::nanoTime);
                try{result=work.run(progress);}
                catch(ProcessCanceledException e){throw e;}
                catch(Exception e){throw new RuntimeException(e);}
            }
            private void finish(){busy=false;controls.forEach(c->c.setEnabled(true));table.setEnabled(true);cancel.setEnabled(false);progressBar.setIndeterminate(false);}
            @Override public void onSuccess(){if(disposed)return;finish();status.setText("完成");progressBar.setValue(1000);success.accept(result);}
            @Override public void onCancel(){if(disposed)return;finish();status.setText("已取消；请刷新确认最终状态，此前完成的文件不会回滚");log("操作已取消；此前已完成的文件保留");}
            @Override public void onThrowable(@NotNull Throwable error){if(disposed)return;finish();error(error.getCause()==null?error:error.getCause());}
        }.queue();
    }
    private void error(Throwable e){String message=e.getMessage()==null?e.toString():e.getMessage();status.setText("失败："+message.lines().findFirst().orElse("未知错误"));log("失败："+message);Messages.showErrorDialog(project,message,"Kubernetes Files");}
    private void log(String text){history.append(java.time.LocalTime.now().withNano(0)+" "+text+"\n");history.setCaretPosition(history.getDocument().getLength());}
    public static String detectKubectl(){for(String p:List.of("/opt/homebrew/bin/kubectl","/usr/local/bin/kubectl","/usr/bin/kubectl"))if(Files.isExecutable(Path.of(p)))return p;return "kubectl";}
    @Override public void dispose(){disposed=true;cancelled.set(true);}
    private static final class EntriesModel extends AbstractTableModel {
        private List<RemoteEntry> entries=List.of();
        void set(List<RemoteEntry> entries){this.entries=entries;fireTableDataChanged();}
        @Override public int getRowCount(){return entries.size();}
        @Override public int getColumnCount(){return 3;}
        @Override public String getColumnName(int c){return switch(c){case 0->"名称";case 1->"类型";default->"大小";};}
        @Override public Object getValueAt(int r,int c){RemoteEntry e=entries.get(r);return switch(c){case 0->e.name().replace("\n","\\n").replace("\r","\\r");case 1->switch(e.type()){case "d"->"目录";case "f"->"文件";case "l"->"符号链接";default->"其他";};default->e.regularFile()?human(e.size()):"—";};}
    }
}
