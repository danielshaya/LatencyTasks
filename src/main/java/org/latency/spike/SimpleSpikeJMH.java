package org.latency.spike;

import net.openhft.chronicle.core.Jvm;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;

/**
 * JMH version of a simple spike - hides coordinated omission.
 */
@State(Scope.Thread)
public class SimpleSpikeJMH {
    private int count = 0;

    public static void main(String[] args) throws InvocationTargetException, IllegalAccessException,
            RunnerException, IOException, ClassNotFoundException {

        if(Jvm.isDebug()){
            SimpleSpikeJMH jmhParse = new SimpleSpikeJMH();
            jmhParse.run();
        }
        else {
            Options opt = new OptionsBuilder()
                    .include(SimpleSpikeJMH.class.getSimpleName())
                    .warmupIterations(3)
                    .forks(1)
                    .measurementIterations(3)
                    .mode(Mode.SampleTime)
                    .measurementTime(TimeValue.seconds(3))
                    .timeUnit(TimeUnit.MICROSECONDS)
                    .build();

            new Runner(opt).run();
        }
    }

    @Benchmark
    public void run() throws IOException, ClassNotFoundException {
        if((count++)%10_000==0){
            //pause a while
            Jvm.busyWaitMicros(10);
        }
    }

}
