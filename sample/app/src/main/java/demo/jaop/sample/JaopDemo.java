package demo.jaop.sample;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import jaop.domain.MethodBodyHook;
import jaop.domain.MethodCallHook;
import jaop.domain.annotation.Before;
import jaop.domain.annotation.Jaop;
import jaop.domain.annotation.Replace;

/**
 * Created by liting06 on 15/12/31.
 */
@Jaop
public class JaopDemo {
    @Replace("demo.jaop.sample.MainActivity.onCreate")
    public void replace1(MethodBodyHook hook) {
        try {
            hook.process();
        } catch (Throwable throwable) {

        }
        Button button = (Button) ((Activity) hook.getTarget()).findViewById(R.id.button);
        button.setText("text replace by jaop");
    }

    @Replace("android.widget.Toast.makeText")
    public void replace2(MethodCallHook hook) {
        Object[] args = hook.getArgs();
        hook.setResult(Toast.makeText((Context) args[0], "hook toast", Toast.LENGTH_LONG));
    }


    @Replace("android.support.v7.app.AppCompatActivity.onCreate")
    public void replace3(MethodCallHook hook) {
        try {
            hook.process();
        } catch (Throwable throwable) {

        }
        Log.e("JaopDemo", "onCreate");
    }

    @Replace("android.app.Activity+.getActionBar")
    public void replace4(MethodBodyHook hook) {
        try {
            hook.process();
        } catch (Throwable throwable) {

        }
        Log.e("JaopDemo", "getActionBar");
    }

    @Replace("demo.jaop.sample.Foo.say")
    public void replace5(MethodCallHook hook) {
        Log.e("JaopDemo", "Foo=5");
    }

    @Replace("demo.jaop.sample.Zoo.new")
    public void replace51(MethodCallHook hook) {
        Log.e("JaopDemo", "Zoo new=51");
        try {
            hook.process();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    @Replace("demo.jaop.sample.Zoo.say")
    public void replace6(MethodCallHook hook) {
        Log.e("JaopDemo", "Foo=6");
    }

    @Replace("demo.jaop.sample.MainActivity.Main.onClick")
    public void replace7(MethodBodyHook hook) {
        Log.e("JaopDemo", "onClick_hook2");
    }
}
