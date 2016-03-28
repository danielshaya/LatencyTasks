package org.latency.spike;

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
import java.util.concurrent.TimeUnit;

/**
 * Created by daniel on 25/03/2016.
 */
@State(Scope.Thread)
public class SimpleSpikeJMH {
    int count = 0;

    public static void main(String[] args) throws InvocationTargetException, IllegalAccessException, RunnerException, IOException, ClassNotFoundException {

        if (OS.isLinux())
            Affinity.setAffinity(2);

        if(Jvm.isDebug()){
            SimpleSpikeJMH jmhParse = new SimpleSpikeJMH();
            jmhParse.run();
        }
        else {
            Options opt = new OptionsBuilder()
                    .include(SimpleSpikeJMH.class.getSimpleName())
                    .warmupIterations(6)
                    .forks(1)
                    .measurementIterations(3)
                    .mode(Mode.SampleTime)
                    .measurementTime(TimeValue.seconds(5))
                    .timeUnit(TimeUnit.MICROSECONDS)
                    .build();

            new Runner(opt).run();
        }
    }

    @Benchmark
    public void run() throws IOException, ClassNotFoundException {
        if((count++)%10_000==0){
            //pause a second
            Jvm.pause(1000);
        }
    }

}
