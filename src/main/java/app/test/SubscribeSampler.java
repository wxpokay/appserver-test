package app.test;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import app.test.TestSocketClient;
import pkt.field.values.Omits;
import pkt.java.BasePacket;
import pkts.ConnectPacket;
import pkts.QuoteUpdatePacket;

import javax.management.loading.PrivateClassLoader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Created by ritawu on 17/1/17.
 */
public class SubscribeSampler extends AbstractJavaSamplerClient {

    private TestSocketClient client;
    private String symbol;
    private String host;
    private  int port;
    private String token ="bqZPs8M12rwv5odXLheZriFoNJCQdKRH5NypIePYK2k7zhmVGyN6+PGGkKfdWtdwQvTqyfX9mpRP4NrDi5yWGmWUVVj8MX2ZJYvxj9YFmCkRvRPzKEvIZlCSz4QEnbVYCHXwJP3YfgkeSZEl5ymcWB7I3QluAim/j84YHZsXkbjdkTAQJN3iZTKomOJMig1ussn7GQzMUNQbjRZp3TZ1GZ38/AFETLVQYNWQfdiLEPhVtT61/FSfmxHaEd7C/tXMG29Mg4G2H2VljywNDD6j6wxzSTXTLwkUJ4FbyAH+A03MExwPc4TVrHY7nBrxA+morYqR1VVZgEfmiZU0mEvEkQ==";


    private BlockingQueue<BasePacket> receivedQuotePackets = new LinkedBlockingQueue<>();

    //每个线程测试前执行一次，做一些初始化工作；
    public void setupTest(JavaSamplerContext arg0) {
        client = new TestSocketClient();//实例化一个socketclient
        TestSocketClient.testRsaTokenDecode();
        host = arg0.getParameter("Host","192.168.4.74");
        port = Integer.parseInt(arg0.getParameter("Port","7009"));
        symbol = arg0.getParameter("Symbol","NTES.US.SC");//获取参数
        token = arg0.getParameter("token",token);//获取参数



        //发送包的监听器
        client.addSendPacketListener(new PacketListener() {
            @Override
            public void onPacket(BasePacket packet) {

            }
        });

        //收到包的监听器
        client.addReceivePacketListener(new PacketListener() {
            @Override
            public void onPacket(BasePacket packet) {
                //判断
                if (packet instanceof QuoteUpdatePacket) {
                    receivedQuotePackets.add(packet);
                }
            }
        });

        try {
            client.start(host, port, -1);
            client.connect("Test1", EWait.Wait);
            Thread.sleep(100);
            client.subscribeQuote(symbol, Omits.OmitInt, Omits.OmitInt,token);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

     /**
     * 设置传入参数
     */
    public Arguments getDefaultParameters() {
        Arguments params = new Arguments();

        params.addArgument("Host", "192.168.4.141");
        params.addArgument("Port", "7009");
        params.addArgument("Symbol", "600519.SH.WM");
        params.addArgument("token",token);

        return params;
    }

    //开始测试，从arg0参数可以获得参数值；
    public SampleResult runTest(JavaSamplerContext arg0) {
        final SampleResult sr = new SampleResult();
        sr.setSampleLabel("Subscribe"+symbol + " 线程"+Thread.currentThread().getId());
        sr.setDataType(SampleResult.TEXT);

        try {
            sr.sampleStart();// jmeter 开始统计响应时间标记

            try {
                BasePacket p = receivedQuotePackets.take();
                sr.setResponseData(p.getPacketType().name()+ " " + p.toJsonString(), null);

                sr.setSuccessful(true);
            } catch (Throwable e) {
                e.printStackTrace();
                sr.setSuccessful(false);
            }
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
        SubscribeSampler test = new SubscribeSampler();
        test.setupTest(arg0);
        test.runTest(arg0);
        test.teardownTest(arg0);
    }
}
