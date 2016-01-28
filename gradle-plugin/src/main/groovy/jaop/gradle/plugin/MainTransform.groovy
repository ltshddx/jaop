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
import javassist.JaopClassPool
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
            compile 'jaop.domain:domain:0.0.5'
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

        JaopClassPool classPool = new JaopClassPool()
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

                box.bodyConfig.findAll { config ->
                    !config.target.handleSubClass || ClassMatcher.chechSuperclass(ctClass, config.target.className)
                }.each { config ->
                    ctClass.declaredMethods.findAll {
                        (config.target.handleSubClass && config.target.methodName == it.name) ||
                                ClassMatcher.match(ctClass.name, it.name, config.target)
                    }.each { ctMethod ->
                        if (config.annotation instanceof Replace) {
                            JaopModifier.bodyReplace(ctMethod, config, outDir)
                        } else if (config.annotation instanceof Before) {
                            JaopModifier.bodyBefore(ctMethod, config, outDir)
                        } else if (config.annotation instanceof After) {
                            JaopModifier.bodyAfter(ctMethod, config, outDir)
                        }
                    }
                }
                if (ctClass.name != "jaop.domain.internal.Cflow")
                    ctClass.writeFile(outDir.absolutePath)
            }
        }.get()
        box.dryClasses.get(0).classPool.get("jaop.domain.internal.Cflow").writeFile(outDir.absolutePath)
    }

}