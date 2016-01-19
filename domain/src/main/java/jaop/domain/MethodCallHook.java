package jaop.domain;

/**
 * Created by liting06 on 15/12/27.
 */
public interface MethodCallHook extends MethodBodyHook {
    public Object process() throws Throwable;

    public Object process(Object[] args) throws Throwable;

    public Object getResult();

    public void setResult(Object result);

    public Object getTarget();

    public Object[] getArgs();

    public Object getThis();
}
