package jaop.domain.internal;

import jaop.domain.MethodCallHook;

/**
 * Created by liting06 on 15/12/27.
 */
public class HookImplForPlugin implements MethodCallHook {
    public Object callThis;
    public Object target;
    public Object result;
    public Object[] args;

    @Override
    public Object getThis() {
        return callThis;
    }

    @Override
    public Object process() throws Throwable {
        return null;
    }

    @Override
    public Object process(Object[] args) throws Throwable {
        return null;
    }

    @Override
    public Object getResult() {
        return result;
    }

    @Override
    public void setResult(Object result) {
        this.result = result;
    }

    @Override
    public Object getTarget() {
        return target;
    }

    @Override
    public Object[] getArgs() {
        return args;
    }
}
