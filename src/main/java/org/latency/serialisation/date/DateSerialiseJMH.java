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
 * Created to show the effects of running code within more complex code.
 * Date serialisation as a micro benchmark vs date serialisation inside a TCP call.
 */
@State(Scope.Thread)
public class DateSerialiseJMH {
    private final Date date = new Date();

    public static void main(String[] args) throws InvocationTargetException,
            IllegalAccessException, RunnerException, IOException, ClassNotFoundException {

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
                    .measurementIterations(5)
                    .mode(Mode.SampleTime)
                    .measurementTime(TimeValue.seconds(3))
                    .timeUnit(TimeUnit.MICROSECONDS)
                    .build();

            new Runner(opt).run();
        }
    }

    @Benchmark
    public Date test() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(out);
        oos.writeObject(date);

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
        return (Date)ois.readObject();
    }
}
