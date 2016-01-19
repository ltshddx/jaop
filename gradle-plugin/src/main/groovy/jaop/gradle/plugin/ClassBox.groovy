package jaop.gradle.plugin

import javassist.CtClass
import javassist.CtMethod;

class ClassBox {
    List<CtClass> dryClasses = new ArrayList<>()
    List<Config> callConfig = new ArrayList<>()
    List<Config> bodyConfig = new ArrayList<>()

    // getDeclaredMethods 的缓存  防止重复创建和死锁  空间换时间
    List<CtMethod[]> methodCache = new ArrayList<>()
}