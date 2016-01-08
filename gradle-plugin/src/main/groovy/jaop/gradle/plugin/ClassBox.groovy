package jaop.gradle.plugin

import javassist.CtClass
import javassist.CtMethod;

class ClassBox {
    List<CtClass> dryClasses = new ArrayList<>()
    List<CtMethod> callConfig = new ArrayList<>()
    List<CtMethod> bodyConfig = new ArrayList<>()
}