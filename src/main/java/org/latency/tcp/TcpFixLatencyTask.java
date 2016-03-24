package org.latency.tcp;

import net.openhft.affinity.Affinity;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.latencybenchmark.LatencyTask;
import net.openhft.chronicle.core.latencybenchmark.LatencyTestHarness;
import net.openhft.chronicle.core.util.NanoSampler;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * Created by daniel on 02/07/2015.
 * Simple program to test loopback speeds and latencies.
 */
/* running on an i7-3790X 3.5 Ghz with Ubuntu 15.04 - low latency

Starting latency test rate: 200000 ... Loop back echo latency 50/90 99/99.9 99.99/99.999 - worst
was 1,577,060 / 2,885,680  3,154,120 / 3,154,120  3,154,120 / 3,154,120 - 3,154,120 micro seconds

Starting latency test rate: 160000 ... Loop back echo latency 50/90 99/99.9 99.99/99.999 - worst
was 5.2 / 13  22 / 31  56 / 109 - 135 micro seconds

Starting latency test rate: 140000 ... Loop back echo latency 50/90 99/99.9 99.99/99.999 - worst
was 5.2 / 7.0  17 / 19  23 / 88 - 135 micro seconds

Starting latency test rate: 120000 ... Loop back echo latency 50/90 99/99.9 99.99/99.999 - worst
was 5.2 / 5.8  17 / 18  22 / 65 - 100 micro seconds

Starting latency test rate: 100000 ... Loop back echo latency 50/90 99/99.9 99.99/99.999 - worst
was 5.2 / 5.8  17 / 18  21 / 76 - 104 micro seconds


On my MBP

Starting latency test rate: 10000
Loop back echo latency 50/90 99/99.9 99.99 - worst
was 23 / 26  54 / 72  319 - 1,930 micro seconds

Starting latency test rate: 10000 ... Loop back echo latency 50/90 99/99.9 99.99 - worst
was 23 / 27  58 / 76  135 - 803 micro seconds

Starting latency test rate: 5000 ... Loop back echo latency 50/90 99/99.9 99.99 - worst
was 23 / 26  60 / 72  434 - 2,060 micro seconds

With the full fix message:

Starting latency test rate: 10000 ... Loop back echo latency 50/90 99/99.9 99.99 - worst
was 25 / 38  68 / 121  1,610 - 4,330 micro seconds

Starting latency test rate: 5000 ... Loop back echo latency 50/90 99/99.9 99.99 - worst
was 24 / 32  65 / 84  336 - 1,470 micro seconds

Starting latency test rate: 2000 ... Loop back echo latency 50/90 99/99.9 99.99 - worst
was 26 / 60  72 / 92  270 - 1,210 micro seconds
 */
public class TcpFixLatencyTask implements LatencyTask {
    public final static int port = 8007;
    public static final boolean BLOCKING = false;
    private final int SERVER_CPU = Integer.getInteger("server.cpu", 0);
    private LatencyTestHarness lth;
    private String fixMessage;
    private byte[] bytesReturned;
    private ByteBuffer bb;
    private SocketChannel socket;
    private byte[] fixMessageBytes;
    private NanoSampler onServer;

    public static void main(String[] args) {
        LatencyTestHarness lth = new LatencyTestHarness()
                .warmUp(50000)
                .messageCount(50000)
                .throughput(10000)
                .runs(5)
                .build(new TcpFixLatencyTask());
        lth.start();
    }

    @Override
    public void init(LatencyTestHarness lth) {
        this.lth = lth;
        onServer = lth.addProbe("on server");
        try {
            runServer(port);
            Jvm.pause(200);

            socket = SocketChannel.open(new InetSocketAddress(port));
            socket.socket().setTcpNoDelay(true);
            socket.configureBlocking(BLOCKING);

        } catch (IOException e) {
            e.printStackTrace();
        }

        fixMessage = "8=FIX.4.2\u00019=211\u000135=D\u000134=3\u000149=MY-INITIATOR-SERVICE\u000152=20160229-" +
                "09:04:14.459\u000156=MY-ACCEPTOR-SERVICE\u00011=ABCTEST1\u000111=863913604164909\u000121=3\u000122=5" +
                "\u000138=1\u000140=2\u000144=200\u000148=LCOM1\u000154=1\u000155=LCOM1\u000159=0\u000160=20160229-09:" +
                "04:14.459\u0001167=FUT\u0001200=201106\u000110=144\u0001\n";

        fixMessageBytes = fixMessage.getBytes();
        int length = fixMessageBytes.length;
        bb = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
        bb.put(fixMessageBytes);
        bytesReturned = new byte[235];
    }

    @Override
    public void complete() {
        System.exit(0);
    }

    private void runServer(int port) throws IOException {

        new Thread(() -> {
            if (SERVER_CPU > 0) {
                System.out.println("server cpu: " + SERVER_CPU);
                Affinity.setAffinity(SERVER_CPU);
            }
            ServerSocketChannel ssc = null;
            SocketChannel socket = null;
            try {
                ssc = ServerSocketChannel.open();
                ssc.bind(new InetSocketAddress(port));
                System.out.println("listening on " + ssc);

                socket = ssc.accept();
                socket.socket().setTcpNoDelay(true);
                socket.configureBlocking(BLOCKING);

                System.out.println("Connected " + socket);

                ByteBuffer bb = ByteBuffer.allocateDirect(32 * 1024).order(ByteOrder.nativeOrder());
                for (; ; ) {
                    bb.limit(12);
                    do {
                        if (socket.read(bb) < 0)
                            throw new EOFException();
                    } while (bb.remaining() > 0);
                    int length = bb.getInt(0);
                    bb.limit(length);
                    do {
                        if (socket.read(bb) < 0)
                            throw new EOFException();
                    } while (bb.remaining() > 0);
                    onServer.sampleNanos(System.nanoTime() - bb.getLong(4));

                    bb.flip();

                    if (socket.write(bb) < 0)
                        throw new EOFException();

                    bb.clear();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                System.out.println("... disconnected " + socket);
                try {
                    if (ssc != null)
                        ssc.close();
                } catch (IOException ignored) {
                }
                try {
                    if (socket != null)
                        socket.close();
                } catch (IOException ignored) {
                }
            }
        }, "server").start();

    }

    @Override
    public void run(long startTimeNs) {
        long value = startTimeNs;
        bb.position(0);
        bb.putInt(bb.remaining());
        bb.putLong(value);
        bb.position(0);
        while (bb.remaining() > 0)
            try {
                if (socket.write(bb) < 0) ;
            } catch (IOException e) {
                e.printStackTrace();
            }

        bb.position(0);
        while (bb.remaining() > 0)
            try {
                if (socket.read(bb) < 0) ;
            } catch (IOException e) {
                e.printStackTrace();
            }

        bb.flip();
        if (bb.getInt(0) != fixMessageBytes.length) {
            throw new AssertionError("read error");
        }

        lth.sample(System.nanoTime() - startTimeNs);
    }
}