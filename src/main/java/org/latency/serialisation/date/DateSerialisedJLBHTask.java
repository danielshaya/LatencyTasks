package org.latency.serialisation.date;

import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;
import net.openhft.chronicle.core.jlbh.JLBH;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;

/**
 * Created to show the effects of running code within more complex code.
 * Date serialisation as a micro benchmark vs date serialisation inside a TCP call.
 */
public class DateSerialisedJLBHTask implements JLBHTask {
    private Date date = new Date();
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
            oos.writeObject(date);

            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
            date = (Date)ois.readObject();
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
