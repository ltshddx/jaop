package jaop.gradle.plugin

import com.android.build.api.transform.Context
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformManager
import jaop.domain.annotation.After
import jaop.domain.annotation.Before
import jaop.domain.annotation.Replace
import javassist.ClassPool
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.concurrent.ForkJoinPool

class MainTransform extends Transform implements Plugin<Project> {
    Project project
    @Override
    void apply(Project target) {
        this.project = target
        project.android.registerTransform(this)
        project.dependencies {
            compile 'jaop.domain:domain:0.0.4'
        }
    }

    @Override
    String getName() {
        return "jaop"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs, TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {
        println '================jaop start================'
        def startTime = System.currentTimeMillis()
        outputProvider.deleteAll()
        def outDir = outputProvider.getContentLocation("main", outputTypes, scopes, Format.DIRECTORY)

        ClassPool classPool = new ClassPool(null)
        project.android.bootClasspath.each {
            classPool.appendClassPath((String)it.absolutePath)
        }

        def box = TransformFileUtils.toCtClasses(inputs, classPool)

        def cost = (System.currentTimeMillis() -startTime) / 1000
        println "check all class cost $cost second, class count ${box.dryClasses.size()}"

        justDoIt(box, outDir)

        cost = (System.currentTimeMillis() -startTime) / 1000
        println "jaop cost $cost second, class count ${box.dryClasses.size()}"
        println '================jaop   end================'
    }

    static void justDoIt(ClassBox box, File outDir) {
        if (box.callConfig.size() == 0 && box.bodyConfig.size() == 0) {
            new ForkJoinPool().submit {
                box.dryClasses.parallelStream().forEach {
                    it.writeFile(outDir.absolutePath)
                }
            }.get()
            return
        }

        new ForkJoinPool().submit{
            box.dryClasses.parallelStream().forEach { ctClass ->
                box.callConfig.stream().filter {
                    ctClass != it.ctMethod.declaringClass
                }.forEach { config ->
                    if (config.annotation instanceof Replace) {
                        JaopModifier.callReplace(ctClass, config, outDir)
                    } else if (config.annotation instanceof Before) {
                        JaopModifier.callBefore(ctClass, config, outDir)
                    } else if (config.annotation instanceof After) {
                        JaopModifier.callAfter(ctClass, config, outDir)
                    }
                }

                if (box.bodyConfig.size() > 0) {
                    ctClass.declaredMethods.each { method ->
                        int replaceCount = 0, beforeCount = 0, afterCount = 0
                        Config replaceConfig = null, beforeConfig = null, afterConfig = null
                        box.bodyConfig.findAll { config ->
                            ClassMatcher.match(ctClass.name, method.name, config.target)
                        } each { config ->
                            if (config.annotation instanceof Replace) {
                                replaceCount ++
                                replaceConfig = config
                            } else if (config.annotation instanceof Before) {
                                beforeCount ++
                                beforeConfig = config
                            } else if (config.annotation instanceof After) {
                                afterCount ++
                                afterConfig = config
                            }
                        }
                        // todo
                        if (replaceCount > 1 || beforeCount > 1 || afterCount >1) {
                            throw new RuntimeException("too many aop method on $ctClass.name.$method.name")
                        } else if (replaceCount + beforeCount > 1 || replaceCount + afterCount > 1) {
                            throw new RuntimeException("replace and before(or after) can not on one method ($ctClass.name.$method.name)")
                        } else if (beforeCount == 1 && afterCount == 1) {
                            JaopModifier.bodyBeforeAndAfter(method, beforeConfig, afterConfig, outDir)
                        } else if (replaceCount == 1) {
                            JaopModifier.bodyReplace(method, replaceConfig, outDir)
                        } else if (beforeCount == 1) {
                            JaopModifier.bodyBefore(method, beforeConfig, outDir)
                        } else if (afterCount == 1) {
                            JaopModifier.bodyAfter(method, afterConfig, outDir)
                        }

                    }
                }
                ctClass.writeFile(outDir.absolutePath)
            }
        }.get()
    }

}