package jaop.gradle.plugin

import jaop.domain.annotation.Jaop
import jaop.domain.annotation.Replace
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import javassist.Modifier

import java.util.stream.Stream

class JaopScanner {
    static List<CtMethod> scanAopConfig(ClassPool classPool, Collection<CtClass> drys) {
        return drys.stream().filter {
            it.getAnnotation(Jaop) != null
        }.flatMap() {
            Stream.<CtMethod>of(it.declaredMethods)
        }.filter {
            it.getAnnotation(Replace) != null
        }.collect {
            if (!Modifier.isPublic(it.modifiers)) {
                it.setModifiers(Modifier.setPublic(it.modifiers))
            }
            return it
        }
    }
}