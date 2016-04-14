package org.latency.prodcon;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.jlbh.JLBH;
import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;
import net.openhft.chronicle.core.util.NanoSampler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;

/**
 *
 */
public class ProducerConsumerJLBHTask implements JLBHTask {

    private final ArrayBlockingQueue<Long> queue = new ArrayBlockingQueue(10000);
    private NanoSampler nanoSampler;


    public static void main(String[] args){
        JLBHOptions lth = new JLBHOptions()
                .warmUpIterations(40_000)
                .iterations(10_000)
                .throughput(1_000)
                .runs(3)
                .recordOSJitter(true)
                .accountForCoordinatedOmmission(true)
                .jlbhTask(new ProducerConsumerJLBHTask());
        new JLBH(lth).start();
    }

    @Override
    public void run(long startTimeNS) {
        try {
            long startTime = System.nanoTime();
            queue.put(startTimeNS);
            nanoSampler.sampleNanos(System.nanoTime() - startTime);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(JLBH lth) {
        nanoSampler = lth.addProbe("put operation");

        Executors.newSingleThreadExecutor().submit(()->{
            while(true) {
                Long startTime = queue.take();
                if (startTime != null) {
                    lth.sample(System.nanoTime() - startTime);
                }else{
                    Jvm.busyWaitMicros(1);
                }
            }
        });
    }
}
