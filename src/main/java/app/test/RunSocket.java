package app.test;
import net.sf.json.JSONObject;
import pkt.field.values.Omits;
import pkt.java.BasePacket;
import pkts.GetChartPacket;
import pkts.QuoteUpdatePacket;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import java.sql.*;


/**
 * Created by ritawu on 17/1/17.
 */

public class RunSocket {
    private static String response;
    private static String request;
    String data = "";
    String token = "bqZPs8M12rwv5odXLheZriFoNJCQdKRH5NypIePYK2k7zhmVGyN6+PGGkKfdWtdwQvTqyfX9mpRP4NrDi5yWGmWUVVj8MX2ZJYvxj9YFmCkRvRPzKEvIZlCSz4QEnbVYCHXwJP3YfgkeSZEl5ymcWB7I3QluAim/j84YHZsXkbjdkTAQJN3iZTKomOJMig1ussn7GQzMUNQbjRZp3TZ1GZ38/AFETLVQYNWQfdiLEPhVtT61/FSfmxHaEd7C/tXMG29Mg4G2H2VljywNDD6j6wxzSTXTLwkUJ4FbyAH+A03MExwPc4TVrHY7nBrxA+morYqR1VVZgEfmiZU0mEvEkQ==";


    private static BlockingQueue<BasePacket> receivedQuotePacket = new LinkedBlockingQueue<>();


    public String getQuote(String symbol) {
        final TestSocketClient client = new TestSocketClient();//实例化一个socketclient
        TestSocketClient.testRsaTokenDecode();
        try {
            client.start("192.168.4.141", 7009, -1);
            client.connect("Test1", EWait.Wait);
            Thread.sleep(100);
            client.subscribeQuote(symbol, Omits.OmitInt, Omits.OmitInt,token);

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
                if (packet instanceof QuoteUpdatePacket) {
                    receivedQuotePacket.add(packet);
                    response = packet.getPacketType().name()+ " " + packet.toJsonString();
                }
            }
        });

        try{

            BasePacket p = receivedQuotePacket.take();
            data = p.toJsonString();
            return data;
        }catch(Throwable e){
            e.printStackTrace();
        }
        return null;
    }

    public String getChart(String symbol,String peroid,String market) {
        String mPeroid = peroid;
        String mMarket = market;
        String mSymbol = symbol;
        String cmd = "/Users/ritawu/Downloads/redis-3.2.6/src/redis-cli -h 192.168.4.142 LINDEX %s_%s_%s 1";
        String newCmd = String.format(cmd, mMarket, mPeroid, mSymbol);
        System.out.println(newCmd);
        Runtime run = Runtime.getRuntime();//返回与当前 Java 应用程序相关的运行时对象
        try {
            Process p = run.exec(newCmd);// 启动另一个进程来执行命令
            BufferedInputStream in = new BufferedInputStream(p.getInputStream());
            BufferedReader inBr = new BufferedReader(new InputStreamReader(in));
            String lineStr;
            while ((lineStr = inBr.readLine()) != null) {
                //获得命令执行后在控制台的输出信息
                System.out.println("K线" + lineStr);// 打印输出信息
                return lineStr;

//                JSONObject jsonObject = JSONObject.fromObject(lineStr);
//                System.out.println(jsonObject);
//                System.out.println("昨收" + jsonObject.get("close"));
//                System.out.println("今开" + jsonObject.get("open"));
            }


            //检查命令是否执行失败。
            if (p.waitFor() != 0) {
                if (p.exitValue() == 1)//p.exitValue()==0表示正常结束，1：非正常结束
                    System.err.println("命令执行失败!");
            }
            inBr.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getRank() throws InterruptedException {
        //BlockingQueue<BasePacket> receivedQuotePacket2 = new LinkedBlockingQueue<>();
        String data;
        TestSocketClient client = new TestSocketClient();//实例化一个socketclient
        TestSocketClient.testRsaTokenDecode();
        try {
            client.start("192.168.4.141", 7009, -1);
            client.connect("Test1", EWait.Wait);
            Thread.sleep(100);
            client.WmGetQuoteAndRank("SH","AMT",0,0);

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
                if (packet instanceof QuoteUpdatePacket) {
                    receivedQuotePacket.add(packet);
                    response = packet.getPacketType().name()+ " " + packet.toJsonString();
                }
            }
        });

        BasePacket p;
        p = receivedQuotePacket.take();
        data = p.toJsonString();
        System.out.println(data);
        return data ;

    }

    public ResultSet getSymbol(){
        ResultSet rs=null;
        //调用Class.forName()方法加载驱动程序
        try {
            Class.forName("com.mysql.jdbc.Driver");
        System.out.println("成功加载MySQL驱动！");

        String host = "jdbc:mysql://192.168.4.152:3306/TA_DS_TEST";    //JDBC的URL
        Connection conn;

        conn = DriverManager.getConnection(host, "tqt001", "tqt001");
        Statement stmt = conn.createStatement(); //创建Statement对象
        System.out.println("成功连接到数据库！");

        //查询数据的代码
        String sql = "select DISTINCT(SYMBOL), TURNOVER from SH_D where SYMBOL NOT LIKE '999%' and KEYTIME >= '2017-03-09' and KEYTIME < '2017-03-10' ORDER BY TURNOVER desc LIMIT 100;";    //要执行的SQL
            rs = stmt.executeQuery(sql);//创建数据对象


//        while (rs.next()){
//            System.out.print(rs.getString(1) + "\t");
//
//            System.out.println();
//        }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rs;



    }


    public static void main(String[] args) throws InterruptedException, ClassNotFoundException, SQLException {
        RunSocket test = new RunSocket();
        ResultSet result = test.getSymbol();
//        String rank;
//        //rank = test.getRank();
        String quoteData = "";
        String quoteOpen = "";
        String quoteClose;
        String dOpen;
        String dClose;

        String symbol = "";
        while (result.next()) {
            symbol = result.getString(1)+".WM";
            System.out.print(symbol  + "\t");
            try {
            quoteData = test.getQuote(symbol);
            JSONObject jsonObject = JSONObject.fromObject(quoteData);
            quoteOpen = (String) jsonObject.getJSONObject("d").get("140");
            quoteClose = (String) jsonObject.getJSONObject("d").get("1025");
            JSONObject jsonObject1 = JSONObject.fromObject(test.getChart(symbol,"D","SH"));
            dOpen = (String)  jsonObject1.get("open");
            dClose = (String) jsonObject1.get("close");
            if (!dOpen.equals(quoteOpen)){
                System.out.println(symbol);
            }
            symbol = (String) jsonObject.get("id");
            } catch (Exception e) {
            e.printStackTrace();
            }

        }

        try{


//            System.out.println("我自己打的"+data);

            Thread.sleep(2000);
            Thread.sleep(2000);
        }catch(Throwable e){
            e.printStackTrace();
        }
        //test.getChart();
        //getChart();
    }

//test.getQuote("600050.SH.WM");


}
