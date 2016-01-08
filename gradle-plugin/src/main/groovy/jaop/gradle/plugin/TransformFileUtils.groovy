package jaop.gradle.plugin

import com.android.SdkConstants
import com.android.build.api.transform.TransformInput
import jaop.domain.MethodBodyHook
import jaop.domain.MethodCallHook
import jaop.domain.annotation.Jaop
import jaop.domain.annotation.Replace
import javassist.ClassPool
import javassist.CtClass
import javassist.Modifier
import org.apache.commons.io.FileUtils

import java.util.jar.JarFile

class TransformFileUtils {
    static ClassBox toCtClasses(Collection<TransformInput> inputs, ClassPool classPool) {
        ClassBox box = new ClassBox()

        inputs.each {
            it.directoryInputs.each {
                def dirPath = it.file.absolutePath
                classPool.insertClassPath(it.file.absolutePath)
                FileUtils.listFiles(it.file, null, true).each {
                    if (it.absolutePath.endsWith(SdkConstants.DOT_CLASS)) {
                        def className = it.absolutePath.substring(dirPath.length() + 1, it.absolutePath.length() - SdkConstants.DOT_CLASS.length()).replaceAll('/', '.')
                        checkCtClass(classPool, box, classPool.get(className))
                    }
                }
            }

            it.jarInputs.stream().each {
                def jarFile = new JarFile(it.file)
                classPool.insertClassPath(it.file.absolutePath)
                jarFile.stream().filter {
                    it.name.endsWith(SdkConstants.DOT_CLASS)
                }.each {
                    def className = it.name.substring(0, it.name.length() - SdkConstants.DOT_CLASS.length()).replaceAll('/', '.')
                    checkCtClass(classPool, box, classPool.get(className))
                }
            }
        }

        return box
    }


    static void checkCtClass(ClassPool classPool, ClassBox box, CtClass ctClass) {
        box.dryClasses.add(ctClass)
        if (ctClass.getAnnotation(Jaop) != null) {
            ctClass.declaredMethods.findAll {
                it.getAnnotation(Replace) != null
            }.each {
                if (!Modifier.isPublic(it.modifiers)) {
                    it.setModifiers(Modifier.setPublic(it.modifiers))
                }
                if (it.parameterTypes.length == 1 && it.parameterTypes[0] == classPool.get(MethodCallHook.name)) {
                    box.callConfig.add(it)
                } else if (it.parameterTypes.length == 1 && it.parameterTypes[0] == classPool.get(MethodBodyHook.name)) {
                    box.bodyConfig.add(it)
                }
            }
        }
    }
}