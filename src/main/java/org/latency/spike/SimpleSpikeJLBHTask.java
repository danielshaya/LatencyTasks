package org.latency.spike;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;
import net.openhft.chronicle.core.jlbh.JLBH;
import org.openjdk.jmh.runner.RunnerException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by daniel on 25/03/2016.
 */
public class SimpleSpikeJLBHTask implements JLBHTask {
    int count = 0;
    private JLBH lth;

    public static void main(String[] args) throws InvocationTargetException, IllegalAccessException, RunnerException, IOException, ClassNotFoundException {
        JLBHOptions lth = new JLBHOptions()
                .warmUpIterations(40_000)
                .iterations(40_000)
                .throughput(10_000)
                .runs(3)
                .recordOSJitter(true)
                .accountForCoordinatedOmmission(false)
                .jlbhTask(new SimpleSpikeJLBHTask());
        new JLBH(lth).start();

    }

    @Override
    public void run(long startTimeNS) {
        if((count++)%10_000==0){
            //pause a second
            Jvm.pause(1000);
        }
        lth.sample(System.nanoTime() - startTimeNS);
    }

    @Override
    public void init(JLBH lth) {
        this.lth = lth;
    }

    @Override
    public void complete() {
    }
}
