package io.github.kubernetesfiles.ui;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.testFramework.LightPlatformTestCase;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class TransferProgressTest extends LightPlatformTestCase {
    public void testGlobalIndicatorReceivesBytesSpeedAndFractionWithoutVisiblePanel() {
        var indicator=new ProgressIndicatorBase();
        var updates=new ArrayList<TransferProgress.Update>();
        var clock=new AtomicLong(1_000_000_000L);
        var progress=new TransferProgress(indicator,()->false,updates::add,clock::get);
        progress.file("archive.war",4096);
        clock.addAndGet(1_000_000_000L);
        progress.transferred(2048);
        assertFalse(indicator.isIndeterminate());
        assertEquals(.5,indicator.getFraction(),.0001);
        assertTrue(indicator.getText().contains("archive.war"));
        assertTrue(indicator.getText2().contains("2.0 KiB / 4.0 KiB"));
        assertTrue(indicator.getText2().contains("2.0 KiB/s"));
        assertEquals(indicator.getText2(),updates.getLast().text());
    }
    public void testNextFileResetsGlobalIndicator() {
        var indicator=new ProgressIndicatorBase();
        var progress=new TransferProgress(indicator,()->false,u->{},System::nanoTime);
        progress.file("first",10);progress.transferred(10);
        progress.file("second",20);
        assertEquals(0d,indicator.getFraction(),.0001);
        assertTrue(indicator.getText().contains("second"));
        assertFalse(indicator.getText2().contains("first"));
    }
    public void testGlobalCancelAndPanelCancelBothStopTransfers() {
        var indicator=new ProgressIndicatorBase();
        var cancelled=new AtomicBoolean();
        var progress=new TransferProgress(indicator,cancelled::get,u->{},System::nanoTime);
        indicator.cancel();
        assertCancelled(progress);
        var second=new TransferProgress(new ProgressIndicatorBase(),cancelled::get,u->{},System::nanoTime);
        cancelled.set(true);
        assertCancelled(second);
    }
    private static void assertCancelled(TransferProgress progress) {
        try{progress.checkCancelled();fail("Cancellation must propagate");}
        catch(ProcessCanceledException expected){}
    }
}
