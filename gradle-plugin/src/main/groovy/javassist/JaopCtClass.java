package javassist;

import java.io.IOException;
import java.io.InputStream;

import javassist.bytecode.ClassFile;

/**
 * Created by liting06 on 16/1/27.
 */
public class JaopCtClass extends CtClassType {
    JaopCtClass(String name, ClassPool cp) {
        super(name, cp);
    }

    JaopCtClass(InputStream ins, ClassPool cp) throws IOException {
        super(ins, cp);
    }

    JaopCtClass(ClassFile cf, ClassPool cp) {
        super(cf, cp);
    }


    private CtMember.Cache members;

    @Override
    protected synchronized CtMember.Cache getMembers() {
        if (members == null)
            members = super.getMembers();
        return members;
    }

    @Override
    void compress() {
//        super.compress();
        // not do it
    }
}
