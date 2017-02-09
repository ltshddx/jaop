# jaop

jaop 是一个基于javassist和asm的gradle aop插件，可以在特定的方法调用处或方法体内 编程 
javassist做简单的代码插入，asm做操作数栈分析和字节码的转录 
不会新增任何方法，秒杀aspectj 
兼容性更好

配置
```groovy
repositories {
  jcenter()
}
dependencies {
    classpath 'com.android.tools.build:gradle:1.5.0' // 需要1.5的plugin
    classpath 'jaop.gradle.plugin:gradle-plugin:0.0.7'
}

apply plugin: 'jaop'
```

用法
```java
@Jaop  //配置文件的开关
public class JaopDemo {
    @After("demo.jaop.sample.MainActivity.onCreate")  // hook 掉onCreate 方法的方法体
    public void replace1(MethodBodyHook hook) {
        Button button = (Button) ((Activity) hook.getTarget()).findViewById(R.id.button);
        button.setText("text replace by jaop");
    }

    @Replace("android.widget.Toast.makeText") // hook Toast makeText 方法的调用处, 替换toast的文本
    public void replace2(MethodCallHook hook) {
        Object[] args = hook.getArgs();
        hook.setResult(Toast.makeText((Context)args[0], "hook toast", Toast.LENGTH_LONG)); // 设置返回值
    }
}
```
详情请看sample
