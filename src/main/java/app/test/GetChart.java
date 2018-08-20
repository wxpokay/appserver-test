package app.test;

import org.apache.jmeter.config.Arguments;//添加参数
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import pkt.java.BasePacket;
import pkts.ChartUpdatePacket;
import pkts.GetChartPacket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Created by ritawu on 17/1/19.
 */
public class GetChart extends AbstractJavaSamplerClient {

    private TestSocketClient client;
    private String symbol;
    private String host;
    private int port;
    private String period;
    private int count;
    private String response;
    private String request;

    private BlockingQueue<BasePacket> receivedQuotePacket = new LinkedBlockingQueue<>();

    /**
     * 初始化
     * 每个线程测试前执行一次，做一些初始化工作
     */
    public void setupTest(JavaSamplerContext arg0) {
        client = new TestSocketClient();//实例化一个socketclient
        TestSocketClient.testRsaTokenDecode();
        //获取参数
        host = arg0.getParameter("Host", "192.168.4.141");
        port = Integer.parseInt(arg0.getParameter("Port", "7009"));
        symbol = arg0.getParameter("Symbol", "600519.SH.WM");
        period = arg0.getParameter("period(DC,1W,1M,3M,1Y,3Y)", "DC");
        count = Integer.parseInt(arg0.getParameter("count","200"));

        try {
            client.start(host, port, -1);
            client.connect("Test1", EWait.Wait);
            Thread.sleep(100);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //发送包
        client.addSendPacketListener(new PacketListener() {
            @Override
            public void onPacket(BasePacket packet) {
                if (packet instanceof GetChartPacket) {
                    request = packet.getPacketType().name() + " " + packet.toJsonString();
                }

            }
        });
        //收到的包
        client.addReceivePacketListener(new PacketListener() {
            @Override
            public void onPacket(BasePacket packet) {
                //判断
                if (packet instanceof ChartUpdatePacket) {
                    receivedQuotePacket.add(packet);
                    response = packet.getPacketType().name()+ " " + packet.toJsonString();
                }
            }
        });
    }

    /**
     * 设置传入参数
     * 可以设置默认值
     */
    public Arguments getDefaultParameters() {
        Arguments params = new Arguments();


        params.addArgument("Host", "192.168.4.141");
        params.addArgument("Port", "");
        params.addArgument("Symbol", "600519.SH.WM");
        params.addArgument("period(DC,1W,1M,3M,1Y,3Y)","DC");//
        params.addArgument("count","200");

        return params;
    }

    /**
     * 线程体
     * 开始测试，从arg0参数可以获得参数值；
     */
    public SampleResult runTest(JavaSamplerContext arg0) {
        final SampleResult sr = new SampleResult();
        //设置线程名称
        sr.setSampleLabel("getChart"+period+symbol + " 线程"+Thread.currentThread().getId());
        sr.setDataType(SampleResult.TEXT);

        try {
            sr.sampleStart();// jmeter 开始统计响应时间标记

            client.getChart(symbol, 2, period,count);
            sr.setSamplerData(request);
            //Thread.sleep(100);
            try {
                BasePacket p = receivedQuotePacket.take();
                sr.setResponseData(p.getPacketType().name()+ " " + p.toJsonString(), null);
                sr.setSuccessful(true);
            } catch (Throwable e) {
                e.printStackTrace();
                sr.setSuccessful(false);
            }
        } catch (Throwable e) {
            sr.setResponseData("runTest Exception",null);
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
        GetChart test = new GetChart();
        test.setupTest(arg0);
        test.runTest(arg0);
        test.teardownTest(arg0);
    }
}
