package jaop.gradle.plugin.bean;

/**
 * Created by liting06 on 2017/2/19.
 *
 * very very ugly, only for some ugly man
 */

public class UglyBean<T> {
    private UglyBean() {}

    public T self;

    public static<T> UglyBean get(T t) {
        UglyBean instace = new UglyBean<T>();
        instace.self = t;
        return instace;
    }
}
