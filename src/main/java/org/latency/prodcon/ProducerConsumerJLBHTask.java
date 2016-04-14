package org.latency.prodcon;

import net.openhft.chronicle.core.jlbh.JLBH;
import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;
import net.openhft.chronicle.core.util.NanoSampler;

import java.util.concurrent.*;

/**
 * Simple test to demonstrate how to use JLBH
 */
public class ProducerConsumerJLBHTask implements JLBHTask {

    private final BlockingQueue<Long> queue = new ArrayBlockingQueue(2);

    private NanoSampler putSampler;
    private NanoSampler pollSampler;
    private volatile boolean completed;


    public static void main(String[] args){
        //Create the JLBH options you require for the benchmark
        JLBHOptions lth = new JLBHOptions()
                .warmUpIterations(40_000)
                .iterations(1_000_000)
                .throughput(100_000)
                .runs(3)
                .recordOSJitter(true)
                .accountForCoordinatedOmmission(true)
                .jlbhTask(new ProducerConsumerJLBHTask());
        new JLBH(lth).start();
    }

    @Override
    public void run(long startTimeNS) {
        try {
            long putSamplerStart = System.nanoTime();
            queue.put(startTimeNS);
            putSampler.sampleNanos(System.nanoTime() - putSamplerStart);
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
                long pollSamplerStart = System.nanoTime();
                Long iterationStart = queue.poll(1, TimeUnit.SECONDS);
                pollSampler.sampleNanos(System.nanoTime() - pollSamplerStart);
                //call back JLBH to signify that the iteration has ended
                lth.sample(System.nanoTime() - iterationStart);
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
