package org.latency.serialisation.date;

import net.openhft.affinity.Affinity;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;
import net.openhft.chronicle.core.jlbh.JLBH;
import net.openhft.chronicle.core.util.NanoSampler;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Date;

/**
 * Created to show the effects of running code within more complex code.
 * Date serialisation as a micro benchmark vs date serialisation inside a TCP call.
 */
public class DateSerialiseJLBHTcpTask implements JLBHTask {
    private final static int port = 8007;
    private static final boolean BLOCKING = false;
    private final int SERVER_CPU = Integer.getInteger("server.cpu", 0);
    private Date date = new Date();
    private JLBH lth;

    private ByteBuffer bb;
    private SocketChannel socket;
    private byte[] fixMessageBytes;
    private NanoSampler dateProbe;

    public static void main(String[] args) {
        JLBHOptions lth = new JLBHOptions()
                .warmUpIterations(50_000)
                .iterations(100_000)
                .throughput(20_000)
                .runs(3)
                .recordOSJitter(true)
                .accountForCoordinatedOmmission(true)
                .jlbhTask(new DateSerialiseJLBHTcpTask());
        new JLBH(lth).start();
    }

    @Override
    public void init(JLBH lth) {
        this.lth = lth;
        dateProbe = lth.addProbe("date serialisation ");
        try {
            runServer(port);
            Jvm.pause(200);

            socket = SocketChannel.open(new InetSocketAddress(port));
            socket.socket().setTcpNoDelay(true);
            socket.configureBlocking(BLOCKING);

        } catch (IOException e) {
            e.printStackTrace();
        }

        String fixMessage = "8=FIX.4.2\u00019=211\u000135=D\u000134=3\u000149=MY-INITIATOR-SERVICE\u000152=20160229-" +
                "09:04:14.459\u000156=MY-ACCEPTOR-SERVICE\u00011=ABCTEST1\u000111=863913604164909\u000121=3\u000122=5" +
                "\u000138=1\u000140=2\u000144=200\u000148=LCOM1\u000154=1\u000155=LCOM1\u000159=0\u000160=20160229-09:" +
                "04:14.459\u0001167=FUT\u0001200=201106\u000110=144\u0001\n";

        fixMessageBytes = fixMessage.getBytes();
        int length = fixMessageBytes.length;
        bb = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
        bb.put(fixMessageBytes);
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

                    long now = System.nanoTime();
                    try {
                        //Running the date serialisation but this time inside the TCP callback.
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(out);
                        oos.writeObject(date);

                        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
                        date = (Date)ois.readObject();
                        dateProbe.sampleNanos(System.nanoTime() - now);
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }

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
        bb.position(0);
        bb.putInt(bb.remaining());
        bb.putLong(startTimeNs);
        bb.position(0);
        writeAll(socket, bb);

        bb.position(0);
        try {
            readAll(socket, bb);
        } catch (IOException e) {
            e.printStackTrace();
        }

        bb.flip();
        if (bb.getInt(0) != fixMessageBytes.length) {
            throw new AssertionError("read error");
        }

        lth.sample(System.nanoTime() - startTimeNs);
    }

    private static void readAll(SocketChannel socket, ByteBuffer bb) throws IOException {
        bb.clear();
        do {
            if (socket.read(bb) < 0)
                throw new EOFException();
        } while (bb.remaining() > 0);
    }

    private static void writeAll(SocketChannel socket, ByteBuffer bb) {
        try {
            while (bb.remaining() > 0 && socket.write(bb) >= 0) ;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}