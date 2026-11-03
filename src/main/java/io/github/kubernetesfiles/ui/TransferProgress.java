package io.github.kubernetesfiles.ui;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import io.github.kubernetesfiles.core.Progress;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Reports to the IDE indicator independently of whether the file panel is visible. */
final class TransferProgress implements Progress {
    record Update(String text,double fraction) {}
    private final ProgressIndicator indicator;
    private final BooleanSupplier cancelled;
    private final Consumer<Update> update;
    private final LongSupplier nanoTime;
    private long total,start,lastUi;
    private String filename="";

    TransferProgress(ProgressIndicator indicator,BooleanSupplier cancelled,Consumer<Update> update,LongSupplier nanoTime){
        this.indicator=indicator;this.cancelled=cancelled;this.update=update;this.nanoTime=nanoTime;
    }
    @Override public void checkCancelled(){
        if(cancelled.getAsBoolean())throw new ProcessCanceledException();
        indicator.checkCanceled();
    }
    void file(String name,long size){
        checkCancelled();filename=name;total=Math.max(0,size);start=nanoTime.getAsLong();lastUi=0;
        indicator.setText(name+" · 传输及 SHA-256 校验");
        indicator.setIndeterminate(false);indicator.setFraction(0);
        indicator.setText2(name+" · 0 B / "+human(total));
    }
    @Override public void transferred(long bytes){
        checkCancelled();double fraction=total>0?Math.min(1d,(double)bytes/total):1d;
        indicator.setFraction(fraction);
        long now=nanoTime.getAsLong();
        if(now-lastUi<100_000_000L && bytes!=total)return;
        lastUi=now;
        String text=filename+" · "+human(bytes)+" / "+human(total)+" · "
                +human((long)(bytes/Math.max(.001,(now-start)/1e9)))+"/s";
        indicator.setText2(text);
        update.accept(new Update(text,fraction));
    }
    static String human(long n){
        if(n<1024)return n+" B";
        if(n<1048576)return String.format(Locale.ROOT,"%.1f KiB",n/1024d);
        return String.format(Locale.ROOT,"%.1f MiB",n/1048576d);
    }
}
