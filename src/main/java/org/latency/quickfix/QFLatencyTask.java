package org.latency.quickfix;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.latencybenchmark.LatencyTask;
import net.openhft.chronicle.core.latencybenchmark.LatencyTestHarness;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix42.ExecutionReport;
import quickfix.fix42.NewOrderSingle;

import java.util.Date;
import java.util.concurrent.Executors;

/**
 * Created by daniel on 19/02/2016.
 * Latency task to test sending a message in QuickFix
 */
public class QFLatencyTask implements LatencyTask {


    private QFClient client;
    private LatencyTestHarness lth;
    private static NewOrderSingle newOrderSingle;
    private static ExecutionReport executionReport;

    public static void main(String[] args) {
        executionReport = new ExecutionReport();
        executionReport.set(new AvgPx(110.11));
        executionReport.set(new CumQty(7));
        executionReport.set(new ClientID("TEST"));
        executionReport.set(new ExecID("tkacct.151124.e.EFX.122.6"));
        executionReport.set(new OrderID("tkacct.151124.e.EFX.122.6"));
        executionReport.set(new Side('1'));
        executionReport.set(new Symbol("EFX"));
        executionReport.set(new ExecType('2'));
        executionReport.set(new ExecTransType('0'));
        executionReport.set(new OrdStatus('0'));
        executionReport.set(new LeavesQty(0));

        newOrderSingle = new NewOrderSingle();

        newOrderSingle.set(new OrdType('2'));
        newOrderSingle.set(new Side('1'));
        newOrderSingle.set(new Symbol("LCOM1"));
        newOrderSingle.set(new HandlInst('3'));
        newOrderSingle.set(new TransactTime(new Date()));
        newOrderSingle.set(new OrderQty(1));
        newOrderSingle.set(new Price(200.0));
        newOrderSingle.set(new TimeInForce('0'));
        newOrderSingle.set(new MaturityMonthYear("201106"));
        newOrderSingle.set(new SecurityType("FUT"));
        newOrderSingle.set(new IDSource("5"));
        newOrderSingle.set(new SecurityID("LCOM1"));
        newOrderSingle.set(new Account("ABCTEST1"));

        LatencyTestHarness lth = new LatencyTestHarness()
                .warmUp(20_000)
                .messageCount(10_000)
                .throughput(5_000)
                .runs(3)
                .build(new QFLatencyTask());
        lth.start();
    }

    @Override
    public void init(LatencyTestHarness lth) {
        this.lth = lth;
        Executors.newSingleThreadExecutor().submit(() ->
        {
            QFServer server = new QFServer();
            server.start();
        });
        Jvm.pause(3000);
        client = new QFClient();
        client.start();
    }

    @Override
    public void complete() {
        System.exit(0);
    }

    @Override
    public void run(long startTimeNs) {
        newOrderSingle.set(new ClOrdID(Long.toString(startTimeNs)));
        try {
            Session.sendToTarget(newOrderSingle, client.sessionId);
        } catch (SessionNotFound sessionNotFound) {
            sessionNotFound.printStackTrace();
        }
    }

    private class QFServer implements Application {
        void start() {
            SocketAcceptor socketAcceptor;
            try {
                SessionSettings executorSettings = new SessionSettings(
                        "src/main/resources/acceptorSettings.txt");
                FileStoreFactory fileStoreFactory = new FileStoreFactory(
                        executorSettings);
                MessageFactory messageFactory = new DefaultMessageFactory();
                FileLogFactory fileLogFactory = new FileLogFactory(executorSettings);
                socketAcceptor = new SocketAcceptor(this, fileStoreFactory,
                        executorSettings, fileLogFactory, messageFactory);
                socketAcceptor.start();
            } catch (ConfigError e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onCreate(SessionID sessionId) {
        }


        @Override
        public void onLogon(SessionID sessionId) {
        }


        @Override
        public void onLogout(SessionID sessionId) {
        }


        @Override
        public void toAdmin(Message message, SessionID sessionId) {
        }


        @Override
        public void fromAdmin(Message message, SessionID sessionId)
                throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
                RejectLogon {
        }


        @Override
        public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        }


        @Override
        public void fromApp(Message message, SessionID sessionId)
                throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
                UnsupportedMessageType {
            try {
                executionReport.set(((NewOrderSingle) message).getClOrdID());
                Session.sendToTarget(executionReport, sessionId);
            } catch (SessionNotFound invalidMessage) {
                invalidMessage.printStackTrace();
            }
        }
    }

    private class QFClient implements Application {
        private SessionID sessionId = null;

        void start() {
            SocketInitiator socketInitiator;
            try {
                SessionSettings sessionSettings = new SessionSettings("src/main/resources/initiatorSettings.txt");
                FileStoreFactory fileStoreFactory = new FileStoreFactory(sessionSettings);
                FileLogFactory logFactory = new FileLogFactory(sessionSettings);
                MessageFactory messageFactory = new DefaultMessageFactory();
                socketInitiator = new SocketInitiator(this,
                        fileStoreFactory, sessionSettings, logFactory,
                        messageFactory);
                socketInitiator.start();
                sessionId = socketInitiator.getSessions().get(0);
                Session.lookupSession(sessionId).logon();
                while (!Session.lookupSession(sessionId).isLoggedOn()) {
                    Thread.sleep(100);
                }
            } catch (Throwable exp) {
                exp.printStackTrace();
            }
        }

        @Override
        public void fromAdmin(Message arg0, SessionID arg1) throws FieldNotFound,
                IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        }


        @Override
        public void fromApp(Message message, SessionID arg1) throws FieldNotFound,
                IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
            long startTime = Long.parseLong(((ExecutionReport) message).getClOrdID().getValue());
            lth.sample(System.nanoTime() - startTime);
        }

        @Override
        public void onCreate(SessionID arg0) {
        }

        @Override
        public void onLogon(SessionID arg0) {
            System.out.println("Successfully logged on for sessionId : " + arg0);
        }

        @Override
        public void onLogout(SessionID arg0) {
            System.out.println("Successfully logged out for sessionId : " + arg0);
        }

        @Override
        public void toAdmin(Message message, SessionID sessionId) {
            boolean result;
            try {
                result = MsgType.LOGON.equals(message.getHeader().getField(new MsgType()).getValue());
            } catch (FieldNotFound e) {
                result = false;
            }
            if (result) {
                ResetSeqNumFlag resetSeqNumFlag = new ResetSeqNumFlag();
                resetSeqNumFlag.setValue(true);
                ((quickfix.fix42.Logon) message).set(resetSeqNumFlag);
            }
        }

        @Override
        public void toApp(Message arg0, SessionID arg1) throws DoNotSend {
        }

    }
}












