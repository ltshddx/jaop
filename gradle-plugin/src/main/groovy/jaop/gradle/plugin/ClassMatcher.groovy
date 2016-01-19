package  jaop.gradle.plugin

import jaop.domain.annotation.Replace
import javassist.expr.MethodCall

class ClassMatcher {
    static boolean match(MethodCall m, TargetMethod target) {
        try {
            def method = m.method
            return match(method.declaringClass.name, method.name, target)
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
}