package jaop.domain;

/**
 * Created by liting06 on 15/12/27.
 */
public interface MethodBodyHook {
    public Object process();

//    public Object getResult();  //process return result

    public void setResult(Object result);

    public Object getTarget();

    public Object[] getArgs();

//    public void setArgs(Object[] args);  not support
}
