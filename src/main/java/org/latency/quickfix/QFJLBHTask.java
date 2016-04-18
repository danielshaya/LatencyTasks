package org.latency.quickfix;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.jlbh.JLBHOptions;
import net.openhft.chronicle.core.jlbh.JLBHTask;
import net.openhft.chronicle.core.jlbh.JLBH;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix42.ExecutionReport;
import quickfix.fix42.NewOrderSingle;

import java.util.Date;
import java.util.concurrent.Executors;

/**
 * Latency task to test sending a message in QuickFix running on Dev server.
 * Intel(R) Xeon(R) CPU E5-2650 v2 @ 2.60GHz
 *
 * Throughput 2000
 Percentile   run1         run2         run3      % Variation   var(log)
 50:           270.34       270.34       233.47         9.52       12.43
 90:           352.26       335.87      1867.78        75.25       16.25
 99:          6684.67      4849.66     69206.02        89.84       24.78
 99.9:       13369.34     12845.06    163577.86        88.67       27.85
 99.99:      81788.93     20447.23    163577.86        82.35       25.46
 worst:     111149.06     98566.14    163577.86        30.54       28.47
 *
 * Throughput 10,000
 Percentile   run1         run2         run3      % Variation   var(log)
 50:           184.32       176.13       176.13         0.00       11.76
 90:           573.44       270.34       249.86         5.18       11.65
 99:         19398.66      2686.98      5111.81        37.56       16.28
 99.9:       28835.84      7733.25      7995.39         2.21       18.50
 99.99:      30932.99      9699.33      9175.04         3.67       19.10
 worst:      30932.99      9699.33      9175.04         3.67       19.10
 *
 * Throughput 50,000
 Percentile   run1         run2         run3      % Variation   var(log)
 50:           176.13       176.13       176.13         0.00       11.82
 90:         12845.06     29884.42      3604.48        82.94       21.01
 99:         34603.01     94371.84     17301.50        74.81       25.26
 99.9:       42991.62     98566.14     25690.11        65.41       25.84
 99.99:      45088.77     98566.14     27787.26        62.94       25.93
 worst:      45088.77     98566.14     27787.26        62.94       25.93

 */
public class QFJLBHTask implements JLBHTask {


    private QFClient client;
    private JLBH lth;
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

        JLBHOptions jlbhOptions = new JLBHOptions()
                .warmUpIterations(20_000)
                .iterations(20_000)
                .throughput(10_000)
                .runs(3)
                .accountForCoordinatedOmmission(false)
                .jlbhTask(new QFJLBHTask());
        new JLBH(jlbhOptions).start();
    }

    @Override
    public void init(JLBH lth) {
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












