package demo.jaop.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Button;
import android.widget.Toast;

import jaop.domain.MethodBodyHook;
import jaop.domain.MethodCallHook;
import jaop.domain.annotation.Jaop;
import jaop.domain.annotation.Replace;

/**
 * Created by liting06 on 15/12/31.
 */
@Jaop
public class JaopDemo {
    @Replace("demo.jaop.sample.MainActivity.onCreate")
    public void replace1(MethodBodyHook hook) {
        hook.process();
        Button button = (Button) ((Activity) hook.getTarget()).findViewById(R.id.button);
        button.setText("text replace by jaop");
    }

//    @Replace("android.widget.Toast.show")
//    public void replace2(MethodCallHook hook) {
//        AlertDialog.Builder builder = new AlertDialog.Builder((Context) hook.getThis());
//        builder.setTitle("jaop");
//        builder.setMessage("jaop hooc success");
//        builder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//
//            }
//        });
//        builder.create().show();
//    }
    @Replace("android.widget.Toast.makeText")
    public void replace2(MethodCallHook hook) {
        Object[] args = hook.getArgs();
        hook.setResult(Toast.makeText((Context)args[0], "hoock toast", Toast.LENGTH_LONG));
    }
}
