package org.latency.serialisation.date;

import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;
import net.openhft.chronicle.core.jlbh.JLBH;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;

/**
 * Created by daniel on 23/03/2016.
 */
public class DateSerialisedJLBHTask implements JLBHTask {
    private Date d = new Date();
    private JLBH lth;

    public static void main(String[] args) throws InvocationTargetException, IllegalAccessException, IOException, ClassNotFoundException {
        JLBHOptions jlbhOptions = new JLBHOptions()
                .warmUpIterations(400_000)
                .iterations(1_000_000)
                .throughput(100_000)
                .runs(3)
                .recordOSJitter(true)
                .accountForCoordinatedOmmission(true)
                .jlbhTask(new DateSerialisedJLBHTask());
        new JLBH(jlbhOptions).start();
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
    public void init(JLBH lth) {
        this.lth = lth;
    }

    @Override
    public void complete() {
    }
}
