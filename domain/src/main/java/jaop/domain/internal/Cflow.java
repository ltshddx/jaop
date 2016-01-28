package jaop.domain.internal;

/**
 * Created by liting06 on 16/1/26.
 */
public class Cflow extends ThreadLocal {
    private static class Depth {
        private int depth;
        Depth() { depth = 0; }
        int get() { return depth; }
        void inc() { ++depth; }
        void dec() { --depth; }
    }

    protected synchronized Object initialValue() {
        return new Depth();
    }

    /**
     * Increments the counter.
     */
    public void enter() { ((Depth)get()).inc(); }

    /**
     * Decrements the counter.
     */
    public void exit() { ((Depth)get()).dec(); }

    /**
     * Returns the value of the counter.
     */
    public int value() { return ((Depth)get()).get(); }
}
