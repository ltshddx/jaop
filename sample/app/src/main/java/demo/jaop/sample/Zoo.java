package demo.jaop.sample;

import android.util.Log;

/**
 * Created by liting06 on 16/1/20.
 */
public class Zoo extends Foo {
    public Zoo() {
        Log.e("init", "Zoo");
    }

    public Zoo(String s) {
        super(s);
        Log.e("init", "Zoo whit param");
    }

    static {
        Log.e("init", "static");
    }

    @Override
    public void say() {
        Log.e("Zoo", "Zoo");
    }
}
