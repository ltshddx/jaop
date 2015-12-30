package jaop.domain;

/**
 * Created by liting06 on 15/12/27.
 */
public interface MethodCallHook extends MethodBodyHook {
    public Object getThis();
}
