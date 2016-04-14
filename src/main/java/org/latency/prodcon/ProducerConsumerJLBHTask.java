package org.latency.prodcon;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.jlbh.JLBH;
import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;
import net.openhft.chronicle.core.util.NanoSampler;

import java.util.concurrent.*;

/**
 *
 */
public class ProducerConsumerJLBHTask implements JLBHTask {

    private final BlockingQueue<Long> queue = new ArrayBlockingQueue(2);

    private NanoSampler putSampler;
    private NanoSampler pollSampler;
    private volatile boolean completed;


    public static void main(String[] args){
        JLBHOptions lth = new JLBHOptions()
                .warmUpIterations(40_000)
                .iterations(100_000)
                .throughput(40_000)
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
            putSampler.sampleNanos(System.nanoTime() - startTime);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(JLBH lth) {
        putSampler = lth.addProbe("put operation");
        pollSampler = lth.addProbe("poll operation");

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(()->{
            while(!completed) {
                long startTime = System.nanoTime();
                Long startTime2 = queue.poll(1, TimeUnit.SECONDS);
                pollSampler.sampleNanos(System.nanoTime() - startTime);
                lth.sample(System.nanoTime() - startTime2);
            }
            return null;
        });
        executorService.shutdown();
    }

    @Override
    public void complete(){
        completed = true;
    }
}
