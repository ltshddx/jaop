package jaop.gradle.plugin

import com.android.SdkConstants
import com.android.build.api.transform.TransformInput
import javassist.ClassPool
import javassist.CtClass
import org.apache.commons.io.FileUtils

import java.util.jar.JarFile

class TransformFileUtils {
    static List<CtClass> toCtClasses(Collection<TransformInput> inputs, ClassPool classPool) {
        List<CtClass> result = new ArrayList<>()
        inputs.each {
            it.directoryInputs.each {
                def dirPath = it.file.absolutePath
                classPool.insertClassPath(it.file.absolutePath)
                FileUtils.listFiles(it.file, null, true).each {
                    if (it.absolutePath.endsWith(SdkConstants.DOT_CLASS)) {
                        def className = it.absolutePath.substring(dirPath.length() + 1, it.absolutePath.length() - SdkConstants.DOT_CLASS.length()).replaceAll('/', '.')
                        result.add(classPool.get(className))
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
                    result.add(classPool.get(className))
                }
            }
        }

        return result
    }
}