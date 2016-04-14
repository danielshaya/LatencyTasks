package org.latency.tcp;

import net.openhft.affinity.Affinity;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;
import net.openhft.chronicle.core.jlbh.JLBH;
import net.openhft.chronicle.core.util.NanoSampler;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TcpBenchmark implements JLBHTask {
    private final static int port = 8007;
    private static final boolean BLOCKING = false;
    private final int SERVER_CPU = Integer.getInteger("server.cpu", 0);
    private JLBH jlbh;
    private ByteBuffer bb;
    private SocketChannel socket;
    private NanoSampler client2serverProbe;
    private NanoSampler server2clientProbe;

    public static void main(String[] args) {
        JLBHOptions jlbhOptions = new JLBHOptions()
                .warmUpIterations(50000)
                .iterations(50000)
                .throughput(20000)
                .runs(5)
                .jlbhTask(new TcpBenchmark());
        new JLBH(jlbhOptions).start();
    }

    @Override
    public void init(JLBH jlbh) {
        this.jlbh = jlbh;
        client2serverProbe = jlbh.addProbe("client2server");
        server2clientProbe = jlbh.addProbe("server2clientProbe");
        try {
            runServer(port);
            Jvm.pause(200);

            socket = SocketChannel.open(new InetSocketAddress(port));
            socket.socket().setTcpNoDelay(true);
            socket.configureBlocking(BLOCKING);

        } catch (IOException e) {
            e.printStackTrace();
        }

        bb = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
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

                ByteBuffer bb = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
                while (true) {
                    readAll(socket, bb);

                    bb.flip();
                    long time = System.nanoTime();
                    client2serverProbe.sampleNanos(time - bb.getLong());
                    bb.clear();
                    bb.putLong(time);
                    bb.flip();

                    writeAll(socket, bb);
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

    private static void readAll(SocketChannel socket, ByteBuffer bb) throws IOException {
        bb.clear();
        do {
            if (socket.read(bb) < 0)
                throw new EOFException();
        } while (bb.remaining() > 0);
    }

    @Override
    public void run(long startTimeNs) {
        bb.position(0);
        bb.putLong(System.nanoTime());
        bb.position(0);
        writeAll(socket, bb);

        bb.position(0);
        try {
            readAll(socket, bb);
            server2clientProbe.sampleNanos(System.nanoTime() - bb.getLong(0));
        } catch (IOException e) {
            e.printStackTrace();
        }

        jlbh.sample(System.nanoTime() - startTimeNs);
    }

    private static void writeAll(SocketChannel socket, ByteBuffer bb) {
        try {
            while (bb.remaining() > 0 && socket.write(bb) >= 0) ;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void complete() {
        System.exit(0);
    }
}