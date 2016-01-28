package  jaop.gradle.plugin

import jaop.domain.annotation.Replace
import javassist.CtClass
import javassist.CtMethod
import javassist.NotFoundException
import javassist.expr.MethodCall

class ClassMatcher {
    static boolean match(MethodCall m, TargetMethod target) {
        try {
//            def method = m.method
            return match(m.className, m.methodName, target)
        } catch (Exception e) {
            return false
        }
    }

    static boolean match(String className, String methodName, TargetMethod target) {
        // 利用正则
        String src = "$className.$methodName"
        def result
        if (target.isRegex) {
            result = src ==~ target.value
        } else {
            result = src.equals(target.value)
        }
        return result
    }

    static boolean chechSuperclass(CtClass ctClass, String targetName) {
        CtClass superclass
        try {
            superclass = ctClass.getSuperclass()
        } catch (NotFoundException e) {
            return false
        }
        if (superclass.name == "java.lang.Object") {
            return false
        } else if (superclass.name == targetName) {
            return true
        } else {
            return chechSuperclass(superclass, targetName)
        }
    }
}