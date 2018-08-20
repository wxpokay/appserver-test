package app.ritatest;

/**
 * Created by ritawu on 17/1/17.
 */

public class Hello {
    public String sayHello()
    {
        return "Hello";
    }
    public String sayHelloToPerson(String s)
    {
        if(s == null || s.equals(""))
            s = "nobody";
        return (new StringBuilder()).append("Hello ").append(s).toString();
    }
    public int sum(int a,int b)
    {
        return a+b;
    }
}