package org.latency.spike;

import net.openhft.affinity.Affinity;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.core.latencybenchmark.LatencyTask;
import net.openhft.chronicle.core.latencybenchmark.LatencyTestHarness;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;

/**
 * Created by daniel on 25/03/2016.
 */
public class SimpleSpikeLatencyTask implements LatencyTask{
    int count = 0;
    private LatencyTestHarness lth;

    public static void main(String[] args) throws InvocationTargetException, IllegalAccessException, RunnerException, IOException, ClassNotFoundException {
        LatencyTestHarness lth = new LatencyTestHarness()
                .warmUp(40_000)
                .messageCount(40_000)
                .throughput(10_000)
                .runs(3)
                .recordOSJitter(true)
                .accountForCoordinatedOmmission(false)
                .build(new SimpleSpikeLatencyTask());
        lth.start();

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
    public void init(LatencyTestHarness lth) {
        this.lth = lth;
    }

    @Override
    public void complete() {
    }
}
