package app.ritatest;

import app.test.EWait;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import app.test.TestSocketClient;

/**
 * Created by ritawu on 17/4/19.
 */
public class EnterOrder extends AbstractJavaSamplerClient{

    private TestSocketClient client;
    private String symbol;
    private String host;
    private  int port;
    String token;


    //每个线程测试前执行一次，做一些初始化工作；
    public void setupTest(JavaSamplerContext arg0) {
        //token = 'ToyrRVE1jAuUiNEVhWkizai+k3LhjwUBhRVRovNcZ1//xMSIvy/6D4JMFIcoWGtvNLotNrYqm5XkFJHGlptVQ/IKfxfjJU9M0ZwRNqW7hU3TGspWAuBsWNxLRz1vD3nr3iJ9ulf4SEwNWqUgjuiPPdJqw2LJliGlrmuSdafmSzLGoAJIuRq6wCQ2QcQfS3EmyG0mLqPmBzCieRPy87Ut9z28j+x4PrjIdjMKtQc+fuK61hCG0V+b4EecTrB7GYbDYiuNAEdzriVOh42OL0M+IgEnKBFl8TXtjeYh2QygRCzfkKfIbLxSxnlmuOOsHuKK/Kq9BLPIpGjISaExbIRsRg=='
        client = new TestSocketClient();//实例化一个socketclient
        TestSocketClient.testRsaTokenDecode();
        host = arg0.getParameter("Host","61.152.93.136");
        port = Integer.parseInt(arg0.getParameter("Port","18882"));
        token = arg0.getParameter("Token","ToyrRVE1jAuUiNEVhWkizai+k3LhjwUBhRVRovNcZ1//xMSIvy/6D4JMFIcoWGtvNLotNrYqm5XkFJHGlptVQ/IKfxfjJU9M0ZwRNqW7hU3TGspWAuBsWNxLRz1vD3nr3iJ9ulf4SEwNWqUgjuiPPdJqw2LJliGlrmuSdafmSzLGoAJIuRq6wCQ2QcQfS3EmyG0mLqPmBzCieRPy87Ut9z28j+x4PrjIdjMKtQc+fuK61hCG0V+b4EecTrB7GYbDYiuNAEdzriVOh42OL0M+IgEnKBFl8TXtjeYh2QygRCzfkKfIbLxSxnlmuOOsHuKK/Kq9BLPIpGjISaExbIRsRg==");
        //token = arg0.getParameter("Token","kkkk");


        try {
            client.start(host, port, -1);
            client.connect("Test1", EWait.Wait);
            Thread.sleep(100);
            client.unionTokenLogin(token);
            Thread.sleep(1000);
//EnterOrder{"reason":0,"side":1,"pt":203,"sym":"00700.HK.SC","price":229,"qty":100,"txid":"A65A427570624FA19B97A8C7ADD3F4FD10029","otype":2,"strategy":1,"behalf":"qq000000071","seq":66,"account":"qq000000071-SG"}
// public void EnterOrderPacket(String account, String sym, int side, int otype, double qty, int strategy, String txid)
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置传入参数
     */
    public Arguments getDefaultParameters() {
        Arguments params = new Arguments();

        params.addArgument("Host", "61.152.93.136");
        params.addArgument("Port", "18882");

        return params;
    }

    //开始测试，从arg0参数可以获得参数值；
    public SampleResult runTest(JavaSamplerContext arg0) {
        final SampleResult sr = new SampleResult();
        sr.setSampleLabel("下单"+symbol + " 线程"+Thread.currentThread().getId());
        sr.setDataType(SampleResult.TEXT);

        try {
            sr.sampleStart();// jmeter 开始统计响应时间标记

            //public void EnterOrderPacket(String account, String sym, int side, int otype, double qty, int strategy)


            client.EnterOrderPacket("qq000000071-SG","00700.HK.SC",1,1,100,1);
            Thread.sleep(5000);
            client.EnterOrderPacket("qq000000071-SG","00700.HK.SC",2,1,100,1);
            Thread.sleep(5000);
            client.disconnect();
            //side:買進或賣出，1為買進(Buy)，2為賣出(Sell)
            //otype:買進或賣出，1為買進(Buy)，2為賣出(Sell)
            //strategy:1 為SDMA （原本的委託都屬這種） ， 2為STOP

        } catch (Throwable e) {
            sr.setSuccessful(false);
            e.printStackTrace();
        } finally {
            sr.sampleEnd();// jmeter 结束统计响应时间标记
        }
        return sr;
    }

    //测试结束时调用；
    public void teardownTest(JavaSamplerContext arg0) {
        client.disconnect();

    }
    public static void main(String[] args) { // TODO Auto-generated method stub
        Arguments params = new Arguments();
        JavaSamplerContext arg0 = new JavaSamplerContext(params);
        EnterOrder test = new EnterOrder();
        test.setupTest(arg0);
        test.runTest(arg0);
        test.teardownTest(arg0);
    }

}
