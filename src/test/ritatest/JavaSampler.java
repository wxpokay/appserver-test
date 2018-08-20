package app.ritatest;
//编写jmeter.sampler插件需加载的包
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
//处理异常堆栈
import java.io.PrintWriter;
import java.io.StringWriter;


/**
 *
 *  继承虚类AbstractJavaSamplerClient
 */
public class JavaSampler extends AbstractJavaSamplerClient {

    // main只是为调试用，最好先调试再打包
//运行前请把jmeter_home/lib下的所有jar包加载到IDE工具环境变量
    public static void main(String[] args)
    {
        Arguments args0 = new Arguments();
        args0.addArgument("parm_1","val_1");
        args0.addArgument("parm_2","val_2");
        args0.addArgument("parm_N","val_N");
        JavaSampler  test = new JavaSampler();
        JavaSamplerContext context = new JavaSamplerContext(args0);
        test.setupTest(context);
        test.runTest(context);
        test.teardownTest(context);
    }

    /**
     *  实现 runTest(JavaSamplerContext context)方法
     *  runTest()具体实施测试动作
     */
    public SampleResult runTest(JavaSamplerContext context)  {
    /*
     *  SampleResult只能定义为局部变量，避免出现多线程安全问题
     *  网上一些帖子，定义为全域变量，这种做法是错误的
     */
        SampleResult results = new SampleResult();
        //默认请求成功
        results.setSuccessful(true);
        results.sampleStart(); //记录响应时间开始
        try{
            //动态变量从context中读取:
            // String key = context.getParameter("key");
            //TO-DO ejb接口调用

            if(false){ //失败时处理
                results.setSuccessful(false);
                results.setResponseData("响应数据","utf8");
            }
        }catch(Throwable e){
            e.printStackTrace();
            results.setSuccessful(false);
            //处理异常堆栈为String，只有String才能回写响应数据
            results.setResponseData(toStringStackTrace(e),"utf8");
        }
        results.sampleEnd(); //记录响应时间结束
        return results;
    }

    /**
     * 测试开始时调用，初始化
     */
    public void setupTest(JavaSamplerContext context){
    }
    /**
     * 测试结束时调用
     */
    public void teardownTest(JavaSamplerContext context){
    }

    /**
     *  定义默认参数
     */
    @Override
    public Arguments getDefaultParameters() {
        Arguments args = new Arguments();
    /*
     * test data
     */
        args.addArgument("parm_1","val_1");
        args.addArgument("parm_2","val_2");
        args.addArgument("parm_N","val_N");
        return args;
    }

    /**
     *  处理异常堆栈为String，只有String才能回写响应数据
     * @param e
     * @return
     */
    private String toStringStackTrace(Throwable e){
        String exception = null;
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            exception = sw.toString();
            pw.close();
            sw.close();
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        return exception;
    }

}