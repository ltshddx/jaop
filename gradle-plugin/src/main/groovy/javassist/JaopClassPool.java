package javassist;

/**
 * Created by liting06 on 16/1/27.
 */
public class JaopClassPool extends ClassPool {
    @Override
    protected CtClass createCtClass(String classname, boolean useCache) {
        CtClass ctClass = super.createCtClass(classname, useCache);
        if (ctClass instanceof CtClassType) {
            return new JaopCtClass(classname, this);
        } else {
            return ctClass;
        }
    }
}
