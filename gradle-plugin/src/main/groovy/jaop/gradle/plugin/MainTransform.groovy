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
import jaop.gradle.plugin.asm.BodyReplaceUtil
import jaop.gradle.plugin.asm.CallReplaceForConstructorUtil
import jaop.gradle.plugin.asm.CallReplaceUtil
import jaop.gradle.plugin.bean.UglyBean
import javassist.CannotCompileException
import javassist.CtClass
import javassist.CtConstructor
import javassist.CtMethod
import javassist.JaopClassPool
import javassist.NotFoundException
import javassist.bytecode.AccessFlag
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import javassist.expr.NewExpr
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.util.concurrent.ConcurrentHashMap
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

        Map<MethodCall, Config> methodCallMap = new ConcurrentHashMap<>()
        Map<MethodCall, Config> newExprMap = new ConcurrentHashMap<>()
        Map<UglyBean<CtMethod>, Config> bodyMap = new ConcurrentHashMap<>()

        new ForkJoinPool().submit{
            box.dryClasses.parallelStream().findAll { ctClass ->
                boolean result = true
                try {
                    ctClass.getSuperclass()
                } catch (Exception e) {
                    result = false
                }
                if (!result) {
                    ctClass.writeFile(outDir)
                }
                return result
            }.forEach { ctClass ->
                box.callConfig.stream().filter {
                    ctClass != it.ctMethod.declaringClass
                }.forEach { config ->
                    ctClass.declaredBehaviors.each {
                        HashSet sameNewHook = new HashSet()
                        HashSet sameCallHook = new HashSet()
                        boolean firstCallSuper = true
                        it.instrument(new ExprEditor() {
                            @Override
                            void edit(MethodCall m) throws CannotCompileException {
                                if (ClassMatcher.match(m, config.target)) {
                                    if (config.annotation instanceof Replace) {
//                                    JaopModifier.callReplace(m, config, wetClasses)
                                        String desc = "$m.className.$m.methodName.$m.method.methodInfo2.descriptor"
                                        if (!sameCallHook.contains(desc)) {
                                            sameCallHook.add(desc)
                                            methodCallMap.put(m, config)
                                        }
                                    } else if (config.annotation instanceof Before) {
                                        JaopModifier.callBefore(m, config)
                                    } else if (config.annotation instanceof After) {
                                        JaopModifier.callAfter(m, config)
                                    }
                                }
                            }

                            @Override
                            void edit(NewExpr e) throws CannotCompileException {
                                if (firstCallSuper && it instanceof CtConstructor &&
                                        (e.className.equals(ctClass.superclass.name) || e.className.equals(ctClass.name))) {
                                    // call foo super(**) or self this(**)
                                    firstCallSuper = false
                                    return
                                }
                                if (ClassMatcher.match(e.className, 'new', config.target)) {
                                    if (config.annotation instanceof Replace) {
//                                    JaopModifier.callReplaceForConstructor(e, config, wetClasses)
                                        String desc = "$e.className.$e.constructor.methodInfo2.descriptor"
                                        if (!sameNewHook.contains(desc)) {
                                            sameNewHook.add(desc)
                                            newExprMap.put(e, config)
                                        }
                                    } else if (config.annotation instanceof Before) {
                                        JaopModifier.callBefore(e, config)
                                    } else if (config.annotation instanceof After) {
                                        JaopModifier.callAfter(e, config)
                                    }
                                }
                            }
                        })
                    }
                }

                box.bodyConfig.findAll { config ->
                    !config.target.handleSubClass || ClassMatcher.chechSuperclass(ctClass, config.target.className)
                }.each { config ->
                    ctClass.declaredBehaviors.findAll {
                        // synthetic 方法暂时不aop 比如AsyncTask 会生成一些同名 synthetic方法
                        // not support synthetic method
                        if ((it.getModifiers() & AccessFlag.SYNTHETIC) != 0) {
                            return false
                        }
                        if ((it.getModifiers() & AccessFlag.ABSTRACT) != 0) {
                            return false
                        }
                        if ((it.getModifiers() & AccessFlag.NATIVE) != 0) {
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
                            // 吐槽下android rxjava 插件 生成的class，堆栈都没空就敢return，敢再懒一点么
                            // 一刀切吧，下次心情好再适配下
                            // in english: fuck rxjava
                            boolean  isSyntheticClass = (ctClass.getModifiers() & AccessFlag.SYNTHETIC) != 0
                            if (!isSyntheticClass && ctBehavior instanceof CtMethod) {
                                bodyMap.put(UglyBean.get(ctBehavior), config)
//                            } else if(ctBehavior instanceof CtConstructor) {
//                                JaopModifier.bodyReplaceForConstructor(ctBehavior, config, wetClasses)
//                                logger.warn "[WARNNING] in $ctClass.name, you can not repalce a constructor, it is very dangerous"
                            }
                        } else if (config.annotation instanceof Before) {
                            JaopModifier.bodyBefore(ctBehavior, config)
                        } else if (config.annotation instanceof After) {
                            JaopModifier.bodyAfter(ctBehavior, config)
                        }
                    }
                }
                ctClass.writeFile(outDir)
            }
        }.get()

        new ForkJoinPool().submit{
            box.wetClasses.parallelStream().forEach { ctClass ->
                ctClass.writeFile(outDir)
            }
        }.get()

        ConcurrentHashMap<String, CtClass> asmMap = new ConcurrentHashMap<>()
        methodCallMap.entrySet().each { entry ->
            def newClass = CallReplaceUtil.doit(entry.key, entry.value)
            asmMap.put(newClass.getName(), newClass)
        }

        newExprMap.entrySet().each { entry ->
            def newClass = CallReplaceForConstructorUtil.doit(entry.key, entry.value)
            asmMap.put(newClass.getName(), newClass)
        }

        bodyMap.entrySet().each {
            try {
                def newClass = BodyReplaceUtil.doit(it.key.self, it.value)
                asmMap.put(newClass.getName(), newClass)
            } catch (RuntimeException e) {
                if (e.getClass() == RuntimeException.class && e.getMessage().startsWith("JSR/RET")) {
                    logger.warn("[WARNNING] " + e.getMessage() + ", at " + it.key.self.getLongName());
                } else {
                    logger.error("[ERROR] " + e.getMessage() + ", at " + it.key.self.getLongName());
//                    throw e;
                }
            }
        }

        asmMap.values().each {
            it.writeFile(outDir)
        }
    }

}