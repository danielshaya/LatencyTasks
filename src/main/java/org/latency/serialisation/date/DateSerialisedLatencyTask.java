package org.latency.serialisation.date;

import net.openhft.chronicle.core.latencybenchmark.LatencyTask;
import net.openhft.chronicle.core.latencybenchmark.LatencyTestHarness;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;

/**
 * Created by daniel on 23/03/2016.
 */
public class DateSerialisedLatencyTask implements LatencyTask{
    private Date d = new Date();
    private LatencyTestHarness lth;

    public static void main(String[] args) throws InvocationTargetException, IllegalAccessException, IOException, ClassNotFoundException {
        LatencyTestHarness lth = new LatencyTestHarness()
                .warmUp(400_000)
                .messageCount(1_000_000)
                .throughput(100_000)
                .runs(3)
                .recordOSJitter(true)
                .accountForCoordinatedOmmission(true)
                .build(new DateSerialisedLatencyTask());
        lth.start();
    }

    @Override
    public void run(long startTimeNS) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(out);
            oos.writeObject(d);

            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
            d = (Date)ois.readObject();
            lth.sample(System.nanoTime() - startTimeNS);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(LatencyTestHarness lth) {
        this.lth = lth;
    }

    @Override
    public void complete() {
    }
}
