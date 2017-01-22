package demo.jaop.sample;

import android.app.ActionBar;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.button).setOnClickListener(this);
        getActionBar();

        Foo f = new Zoo("123");
        f.say();
    }

    @Nullable
    @Override
    public ActionBar getActionBar() {
        ActionBar actionBar = super.getActionBar();
        return actionBar;
    }

    @Override
    public void onClick(View v) {
        Toast.makeText(this, "onClick", Toast.LENGTH_LONG).show();
    }
}
