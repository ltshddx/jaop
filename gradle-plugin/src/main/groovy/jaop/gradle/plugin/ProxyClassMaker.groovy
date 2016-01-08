package jaop.gradle.plugin

import jaop.domain.annotation.Replace
import jaop.domain.internal.HookImplForPlugin
import javassist.CtClass
import javassist.CtConstructor
import javassist.CtField
import javassist.CtMethod
import javassist.Modifier

class ProxyClassMaker {

    static def make(CtMethod ctMethod, Replace replace, File outDir) {
        def classPool = ctMethod.declaringClass.classPool
        String className = HookImplForPlugin.class.name + '$Impl' + (ctMethod.hashCode() + Integer.MAX_VALUE)
        className = className.replace('-', '_')
        synchronized (ctMethod) {
            def makeClass = classPool.getOrNull(className)
            if (makeClass == null) {
                makeClass = classPool.makeClass(className)
                makeClass.setSuperclass(classPool.get(HookImplForPlugin.class.name))

                // 参数列表 第一个参数是调用者， 后面是参数列表
                def params = new ArrayList<CtClass>()
                def targetClass = classPool.get(ctMethod.declaringClass.name)
                params.add(targetClass)
                params.addAll(ctMethod.parameterTypes)

                // 参数列表
                makeClass.addField(CtField.make("private $targetClass.name target;", makeClass))
                ctMethod.parameterTypes.eachWithIndex { CtClass entry, int i ->
                    makeClass.addField(CtField.make("private $entry.name param$i;", makeClass))
                }

                // 构造方法
                def paramBody = ''
                def argesParam = ''

                CtConstructor constructor = new CtConstructor(params as CtClass[], makeClass)
                def constructorBody = 'target = $1;'
                ctMethod.parameterTypes.eachWithIndex { CtClass entry, int i ->
                    def paramIndex = i + 2;
                    constructorBody += "param$i = \$$paramIndex;"
                    paramBody += "param$i,"
                    // 基本数据类型在转化object时  dex工具会出错
                    argesParam += "(\$w)param$i,"
                }
                if (paramBody != '') {
                    paramBody = paramBody.substring(0, paramBody.length() - 1)
                    argesParam = argesParam.substring(0, argesParam.length() - 1)
                    constructorBody += "args = new Object[] {$argesParam};"
                }


                constructor.setBody("{$constructorBody}")
                makeClass.addConstructor(constructor)

                // create getTarget
                CtMethod getTarget = new CtMethod(classPool.get(Object.class.name), 'getTarget', null, makeClass)
                getTarget.setBody('{return target;}')
                makeClass.addMethod(getTarget)

                // create setThis
                CtMethod setThis = new CtMethod(CtClass.voidType, 'setThis', classPool.get(Object.class.name) as CtClass[], makeClass)
                setThis.setBody('{callThis = $1;}')
                makeClass.addMethod(setThis)

                // create process method
                CtMethod process = new CtMethod(classPool.get(Object.class.name), 'process', null, makeClass)
                def returnBodyPrefix = ''
                if (ctMethod.returnType != CtClass.voidType) {
                    // 基本数据类型在转化object时  dex工具会出错
                    returnBodyPrefix = "result = (\$w)"
                }

                if (Modifier.isStatic(ctMethod.modifiers)) {
                    process.setBody("{$returnBodyPrefix $ctMethod.declaringClass.name.$ctMethod.name($paramBody);return result;}")
                } else {
                    process.setBody("{$returnBodyPrefix target.$ctMethod.name($paramBody); return result;}")
                }
                makeClass.addMethod(process)
                makeClass.freeze()
                makeClass.writeFile(outDir.absolutePath)
                println "make proxy class for $ctMethod.declaringClass.name.$ctMethod.name, name is $makeClass.name"
            }
            return makeClass
        }
    }
}