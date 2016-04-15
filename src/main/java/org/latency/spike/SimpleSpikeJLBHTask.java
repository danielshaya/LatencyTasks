package org.latency.spike;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.jlbh.JLBH;
import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;

/**
 * A simple JLBH example to show the effects od accounting for co-ordinated omission.
 * Toggle the accountForCoordinatedOmission to see results.
 */
public class SimpleSpikeJLBHTask implements JLBHTask {
    private int count = 0;
    private JLBH lth;

    public static void main(String[] args){
        JLBHOptions lth = new JLBHOptions()
                .warmUpIterations(40_000)
                .iterations(1_100_000)
                .throughput(100_000)
                .runs(3)
                .recordOSJitter(true)
                .accountForCoordinatedOmmission(true)
                .jlbhTask(new SimpleSpikeJLBHTask());
        new JLBH(lth).start();
    }

    @Override
    public void run(long startTimeNS) {
        if((count++)%10_000==0){
            //pause a while
            Jvm.busyWaitMicros(1000);
        }
        lth.sample(System.nanoTime() - startTimeNS);
    }

    @Override
    public void init(JLBH lth) {
        this.lth = lth;
    }
}
