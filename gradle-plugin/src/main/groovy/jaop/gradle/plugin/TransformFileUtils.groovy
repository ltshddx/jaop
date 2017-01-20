package jaop.gradle.plugin

import com.android.SdkConstants
import com.android.build.api.transform.TransformInput
import jaop.domain.MethodBodyHook
import jaop.domain.MethodCallHook
import jaop.domain.annotation.After
import jaop.domain.annotation.Before
import jaop.domain.annotation.Jaop
import jaop.domain.annotation.Replace
import javassist.ClassPool
import javassist.CtClass
import javassist.Modifier
import org.apache.commons.io.FileUtils

import java.util.jar.JarFile

class TransformFileUtils {
    private static final String METHODCALLHOOK_NAME = MethodCallHook.name
    private static final String METHODBODYHOOK_NAME = MethodBodyHook.name

    static ClassBox toCtClasses(Collection<TransformInput> inputs, ClassPool classPool) {
        List<String> classNames = new ArrayList<>()
        def startTime = System.currentTimeMillis()
        inputs.each {
            it.directoryInputs.each {
                def dirPath = it.file.absolutePath
                classPool.insertClassPath(it.file.absolutePath)
                FileUtils.listFiles(it.file, null, true).each {
                    if (it.absolutePath.endsWith(SdkConstants.DOT_CLASS)) {
                        def className = it.absolutePath.substring(dirPath.length() + 1, it.absolutePath.length() - SdkConstants.DOT_CLASS.length()).replaceAll('/', '.')
                        classNames.add(className)
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
                    classNames.add(className)
                }
            }
        }
        def cost = (System.currentTimeMillis() -startTime) / 1000
        println "read all class file cost $cost second"
        ClassBox box = new ClassBox()
        classNames.each {
            checkCtClass(classPool, box, classPool.get(it))
        }

        return box
    }


    static void checkCtClass(ClassPool classPool, ClassBox box, CtClass ctClass) {
        box.dryClasses.add(ctClass)
//        box.methodCache.add(ctClass.declaredMethods)
        if (ctClass.getAnnotation(Jaop) != null) {
            ctClass.declaredMethods.each {

                Object object = it.getAnnotation(Replace)
                if (object == null) {
                    object = it.getAnnotation(Before)
                }
                if (object == null) {
                    object = it.getAnnotation(After)
                }
                if (object != null) {
                    Config config = new Config()

                    // Annotation
                    String match = object.value().trim()
                    config.target = new TargetMethod()
                    String[] splits = match.split("[+][.]")
                    if (splits.length == 2) {
                        config.target.className = splits[0]
                        config.target.methodName = splits[1]
                        config.target.handleSubClass = true
                    } else if (match.contains('*')) {
                        println "has regex $ctClass.name $it.name"
                        match = match.replace('**', ".+")
                        match = match.replace('*', '\\w+')
                        config.target.isRegex = true
                    }
                    config.target.value = match
                    config.annotation = object
                    config.ctMethod = it

                    if (it.parameterTypes.length == 1 && it.parameterTypes[0].name == METHODCALLHOOK_NAME) {
                        box.callConfig.add(config)
                    } else if (it.parameterTypes.length == 1 && it.parameterTypes[0].name == METHODBODYHOOK_NAME) {
                        box.bodyConfig.add(config)
                    }

                    if (!Modifier.isPublic(it.modifiers)) {
                        it.setModifiers(Modifier.setPublic(it.modifiers))
                    }

                    // 把cflow写在config method里面
                    if (config.target.handleSubClass && config.annotation instanceof Replace) {
                        def returnFlag = "return;"
                        if (it.returnType != CtClass.voidType) {
                            returnFlag = "return \$1.getResult();"
                        }
                        def cflowField = JaopModifier.getCflowField(classPool, config)
                        it.insertBefore("""
                                int jaophaonb = ${cflowField}.value();
                                ${cflowField}.enter();
                                if (jaophaonb != 0) {
                                    \$1.process();
                                    ${returnFlag}
                                }
                                """)
                        it.insertAfter("${cflowField}.exit();", true)
                    }
                }
            }
            ctClass.fields.each {
                if (!Modifier.isPublic(it.modifiers)) {
                    it.setModifiers(Modifier.setPublic(it.modifiers))
                }
            }
        }
    }
}