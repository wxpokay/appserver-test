package app.test;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import pkt.java.BasePacket;
import pkts.ChartUpdatePacket;
import pkts.GetChartPacket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Created by ritawu on 17/3/1.
 */
public class Connect extends AbstractJavaSamplerClient{
    private TestSocketClient client;
    private String symbol;
    private String host;
    private int port;
    private String period;
    private int count;
    private String response;
    private String request;

    public void setupTest(JavaSamplerContext arg0){
        client = new TestSocketClient();
        TestSocketClient.testRsaTokenDecode();

        host = arg0.getParameter("Host", "");
        port = Integer.parseInt(arg0.getParameter("Port", ""));
        symbol = arg0.getParameter("Symbol", "");//获取参数
        period = arg0.getParameter("period(DC,1W,1M,3M,1Y,3Y)", "");
        count = Integer.parseInt(arg0.getParameter("count",""));


    }

    public SampleResult runTest(JavaSamplerContext arg0){
        return null;

    }

    public void teardownTest(JavaSamplerContext arg0){

    }


}
