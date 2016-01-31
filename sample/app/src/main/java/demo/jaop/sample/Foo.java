package demo.jaop.sample;

import android.util.Log;

/**
 * Created by liting06 on 16/1/20.
 */
public class Foo {
    public Foo() {
        Log.e("init", "Foo");
    }

    public Foo(String s) {
        Log.e("init", "Foo whit param");
    }

    public void say() {
        Log.e("Foo", "Foo");
    }
}
