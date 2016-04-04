package org.latency.spike;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.jlbh.JLBH;
import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;

/**
 * A simple JLBH example to show the effects od accounting for co-ordinated omission.
 * Toggle the property account.for.co to see results.
 */
public class SimpleSpikeJLBHTask implements JLBHTask {
    private int count = 0;
    private JLBH lth;
    private final static boolean accountForCO = Boolean.getBoolean("account.for.co");

    public static void main(String[] args){
        JLBHOptions lth = new JLBHOptions()
                .warmUpIterations(40_000)
                .iterations(40_000)
                .throughput(100_000)
                .runs(3)
                .recordOSJitter(true)
                .accountForCoordinatedOmmission(accountForCO)
                .jlbhTask(new SimpleSpikeJLBHTask());
        new JLBH(lth).start();
    }

    @Override
    public void run(long startTimeNS) {
        if((count++)%10_000==0){
            //pause a while
            Jvm.busyWaitMicros(10);
        }
        lth.sample(System.nanoTime() - startTimeNS);
    }

    @Override
    public void init(JLBH lth) {
        this.lth = lth;
    }
}
