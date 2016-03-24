package org.latency.serialisation.date;

import net.openhft.affinity.Affinity;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.OS;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by daniel on 22/03/2016.
 */
@State(Scope.Thread)
public class DateSerialiseJMH {
    private Date d = new Date();
    static ByteArrayOutputStream out = null;
    static ObjectOutputStream oos = null;

    public static void main(String[] args) throws InvocationTargetException, IllegalAccessException, RunnerException, IOException, ClassNotFoundException {

        if (OS.isLinux())
            Affinity.setAffinity(2);

        if(Jvm.isDebug()){
            DateSerialiseJMH jmhParse = new DateSerialiseJMH();
            jmhParse.test();
        }
        else {
            Options opt = new OptionsBuilder()
                    .include(DateSerialiseJMH.class.getSimpleName())
                    .warmupIterations(6)
                    .forks(1)
                    .measurementIterations(10)
                    .mode(Mode.Throughput)
                    .measurementTime(TimeValue.seconds(5))
                    .timeUnit(TimeUnit.SECONDS)
                    .build();

            new Runner(opt).run();
        }
    }

    @Benchmark
    public void test() throws IOException, ClassNotFoundException {
        out = new ByteArrayOutputStream();
        oos = new ObjectOutputStream(out);
        oos.writeObject(d);

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
        d = (Date)ois.readObject();
    }
}
