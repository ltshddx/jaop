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
import javassist.CannotCompileException
import javassist.CtClass
import javassist.CtConstructor
import javassist.CtMethod
import javassist.JaopClassPool
import javassist.bytecode.AccessFlag
import javassist.bytecode.SyntheticAttribute
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import javassist.expr.NewExpr
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger

import java.lang.reflect.Modifier
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ForkJoinPool

class MainTransform extends Transform implements Plugin<Project> {
    Project project
    static Logger logger

    @Override
    void apply(Project target) {
        this.project = target
        project.android.registerTransform(this)
        project.dependencies {
            compile 'jaop.domain:domain:0.0.5'
        }
        logger = project.logger
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
        println "check all class cost $cost second, class count: ${box.dryClasses.size()}"

        justDoIt(box, outDir.absolutePath)

        cost = (System.currentTimeMillis() -startTime) / 1000
        println "jaop cost $cost second"
        println '================jaop   end================'
    }

    static void justDoIt(ClassBox box, String outDir) {
        if (box.callConfig.size() == 0 && box.bodyConfig.size() == 0) {
            new ForkJoinPool().submit {
                box.dryClasses.parallelStream().forEach {
                    it.writeFile(outDir)
                }
            }.get()
            return
        }

        List<CtClass> wetClasses = new CopyOnWriteArrayList<CtClass>()

        new ForkJoinPool().submit{
            box.dryClasses.parallelStream().forEach { ctClass ->
                box.callConfig.stream().filter {
                    ctClass != it.ctMethod.declaringClass
                }.forEach { config ->
                    ctClass.instrument(new ExprEditor() {
                        @Override
                        void edit(MethodCall m) throws CannotCompileException {
                            if (ClassMatcher.match(m, config.target)) {
                                if (config.annotation instanceof Replace) {
                                    JaopModifier.callReplace(m, config, wetClasses)
                                } else if (config.annotation instanceof Before) {
                                    JaopModifier.callBefore(m, config, wetClasses)
                                } else if (config.annotation instanceof After) {
                                    JaopModifier.callAfter(m, config, wetClasses)
                                }
                            }
                        }

                        @Override
                        void edit(NewExpr e) throws CannotCompileException {
                            if (ClassMatcher.match(e.className, 'new', config.target)) {
                                if (config.annotation instanceof Replace) {
                                    JaopModifier.callReplaceForConstructor(e, config, wetClasses)
                                } else if (config.annotation instanceof Before) {
                                    JaopModifier.callBefore(e, config, wetClasses)
                                } else if (config.annotation instanceof After) {
                                    JaopModifier.callAfter(e, config, wetClasses)
                                }
                            }
                        }
                    })
                }

                box.bodyConfig.findAll { config ->
                    !config.target.handleSubClass || ClassMatcher.chechSuperclass(ctClass, config.target.className)
                }.each { config ->
                    ctClass.declaredBehaviors.findAll {
                        // synthetic 方法暂时不aop 比如AsyncTask 会生成一些同名 synthetic方法
                        if ((it.getModifiers() & AccessFlag.SYNTHETIC) != 0) {
                            return false
                        }
                        def methodName = it.name
                        if (methodName == ctClass.getSimpleName()) {
                            methodName = 'new'
                        }
                        return (config.target.handleSubClass && config.target.methodName == methodName) ||
                                ClassMatcher.match(ctClass.name, methodName, config.target)
                    }.each { ctBehavior ->
                        if (config.annotation instanceof Replace) {
                            if (ctBehavior instanceof CtMethod)
                                JaopModifier.bodyReplace(ctBehavior, config, wetClasses)
                            else if(ctBehavior instanceof CtConstructor)
                                JaopModifier.bodyReplaceForConstructor(ctBehavior, config, wetClasses)
                        } else if (config.annotation instanceof Before) {
                            JaopModifier.bodyBefore(ctBehavior, config, wetClasses)
                        } else if (config.annotation instanceof After) {
                            JaopModifier.bodyAfter(ctBehavior, config, wetClasses)
                        }
                    }
                }
                ctClass.writeFile(outDir)
            }
        }.get()
        println "jaop create new class count: " + wetClasses.size()
        wetClasses.each {
            it.writeFile(outDir)
        }
    }

}