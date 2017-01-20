package jaop.gradle.plugin.asm;

import org.objectweb.asm.ClassWriter;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

/**
 * Created by liting06 on 2017/1/19.
 */

public class JaopClassWriter extends ClassWriter {
    ClassPool classPool;

    public JaopClassWriter(int flags, ClassPool classPool) {
        super(flags);
        this.classPool = classPool;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            return super.getCommonSuperClass(type1, type2);
        } catch (Exception e2) {
            CtClass c, d;
            try {
                c = classPool.get(type1.replace('/', '.'));
                d = classPool.get(type2.replace('/', '.'));
            } catch (Exception e) {
                throw new RuntimeException(e.toString());
            }
            if (isAssignableFrom(c, d)) {
                return type1;
            }
            if (isAssignableFrom(d, c)) {
                return type2;
            }
            if (c.isInterface() || d.isInterface()) {
                return "java/lang/Object";
            } else {
                do {
                    try {
                        CtClass superclass = c.getSuperclass();
                        if (superclass == null) {
                            break;
                        }
                        c = superclass;
                    } catch (NotFoundException e) {
                        throw new RuntimeException(e.toString());
                    }
                } while (!isAssignableFrom(c, d));
                return c.getName().replace('.', '/');
            }
        }
    }

    private boolean isAssignableFrom(CtClass c, CtClass d) {
        try {
            if (c.getName().equals(d.getName())) {
                return true;
            }
            if (d.getSuperclass() != null && c.getName().equals(d.getSuperclass().getName())) {
                return true;
            }
            for (CtClass e : d.getInterfaces()) {
                if (c.getName().equals(e.getName())) {
                    return true;
                }
            }
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }
}
