package jaop.domain.internal;

import jaop.domain.MethodCallHook;

/**
 * Created by liting06 on 15/12/27.
 */
public abstract class HookImplForPlugin implements MethodCallHook {
    protected Object callThis;
    protected Object result;
    protected Object[] args;

    @Override
    public Object getThis() {
        return callThis;
    }

    @Override
    public abstract Object process();

//    @Override
    public Object getResult() {
        return result;
    }

    @Override
    public void setResult(Object result) {
        this.result = result;
    }

    @Override
    public abstract Object getTarget();

    @Override
    public Object[] getArgs() {
        return args;
    }

//    @Override
//    public void setArgs(Object[] args) {
//        this.args = args;
//    }
}
