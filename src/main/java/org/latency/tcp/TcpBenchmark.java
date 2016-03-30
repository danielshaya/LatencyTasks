package org.latency.tcp;

import net.openhft.affinity.Affinity;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;
import net.openhft.chronicle.core.jlbh.JLBH;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TcpBenchmark implements JLBHTask {
    public final static int port = 8007;
    public static final boolean BLOCKING = false;
    private final int SERVER_CPU = Integer.getInteger("server.cpu", 0);
    private JLBH lth;
    private ByteBuffer bb;
    private SocketChannel socket;

    public static void main(String[] args) {
        JLBHOptions jlbhOptions = new JLBHOptions()
                .warmUpIterations(50000)
                .iterations(50000)
                .throughput(10000)
                .runs(5)
                .jlbhTask(new TcpBenchmark());
        new JLBH(jlbhOptions).start();
    }

    @Override
    public void init(JLBH lth) {
        this.lth = lth;
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

//    int counter = 0;

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

                ByteBuffer bb = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
                for (; ; ) {

                    do {
                        if (socket.read(bb) < 0)
                            throw new EOFException();
                    } while (bb.remaining() > 0);

                    bb.flip();
                    long l = bb.getLong();
                    bb.position(0);
                    bb.limit(8);

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
        bb.position(0);
        bb.putLong(System.nanoTime());
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

            //if (bb.getLong(0) != 0x123456789ABCDEFL)
            //    throw new AssertionError("read error");

        bb.flip();


        lth.sample(System.nanoTime() - startTimeNs);
    }
}