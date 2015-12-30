package jaop.gradle.plugin

import com.android.build.api.transform.Context
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformManager
import jaop.domain.MethodBodyHook
import jaop.domain.MethodCallHook
import jaop.domain.annotation.Replace
import javassist.CannotCompileException
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import javassist.Modifier
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.AttributeInfo
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import org.gradle.api.Plugin
import org.gradle.api.Project;

class MainTransform extends Transform implements Plugin<Project> {
    Project project
    @Override
    void apply(Project target) {
        this.project = target
        project.android.registerTransform(this)
        project.dependencies {
            compile 'jaop.domain:domain:0.0.2'
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

        def dryClasses = TransformFileUtils.toCtClasses(inputs, classPool)

        // todo 可以优化进上面的方法
        def configs = JaopScanner.scanAopConfig(classPool, dryClasses)

        dryClasses.addAll(justDoIt(dryClasses, configs, classPool))

        dryClasses.each {
            try {
                it.writeFile(outDir.absolutePath)
            } catch (Exception e) {
                println it
                throw e
            }
        }
        def cost = (System.currentTimeMillis() -startTime) / 1000
        println "jaop cost $cost second, class count ${dryClasses.size()}"
        println '================jaop   end================'
    }

    static def justDoIt(List<CtClass> list, List<CtMethod> configs, ClassPool classPool) {
        List<CtMethod> callConfig = new ArrayList<>()
        List<CtMethod> bodyConfig = new ArrayList<>()
        List<CtClass> wetClasses = new ArrayList<>()

        configs.each {
            if (it.parameterTypes.length == 1 && it.parameterTypes[0] == classPool.get(MethodCallHook.name)) {
                callConfig.add(it)
            } else if (it.parameterTypes.length == 1 && it.parameterTypes[0] == classPool.get(MethodBodyHook.name)) {
                bodyConfig.add(it)
            }
        }

        if (callConfig.size() == 0 && bodyConfig.size() == 0) {
            return wetClasses
        }

        list.each { ctClass ->
            callConfig.findAll {
                ctClass != it.declaringClass
            }.each { config ->
                ctClass.instrument(new ExprEditor() {
                    @Override
                    void edit(MethodCall m) throws CannotCompileException {
                        Replace replace = (Replace)config.getAnnotation(Replace)
                        if (ClassMatcher.match(m.className, m.methodName, replace)) {
                            def makeClass = ProxyClassMaker.make(m.method, replace)

                            wetClasses.add(makeClass)
                            // 静态方法 没有this
                            def thisFlag = ''
                            if (!m.withinStatic()) {
                                thisFlag = 'makeclass.setThis(this);'
                            }
                            def body = "$makeClass.name makeclass = new $makeClass.name(\$0, \$\$);" +
                                    thisFlag +
                                    " new $config.declaringClass.name().$config.name(makeclass);" +
                                    "\$_ = (\$r)makeclass.getResult();"
                            m.replace(body)
                        }
                    }
                })
            }

            bodyConfig.findAll {
                ctClass != it.declaringClass
            }.each { config ->
                ctClass.declaredMethods.findAll { method ->
                    ClassMatcher.match(method.declaringClass.name, method.name, (Replace)config.getAnnotation(Replace))
                }.each {
                    // fixbug aop body failed
                    CtMethod realSrcClass = new CtMethod(it.returnType, it.name, it.parameterTypes, it.declaringClass)
                    it.setName((it.name + '_jaop_create_' + it.hashCode()).replaceAll('-', '_'))
                    realSrcClass.setModifiers(it.modifiers)
                    it.declaringClass.addMethod(realSrcClass)
                    it.setModifiers(Modifier.setPublic(it.modifiers))

                    // repalce annotations
                    def visibleTag = it.getMethodInfo().getAttribute(AnnotationsAttribute.visibleTag);
                    def invisibleTag = it.getMethodInfo().getAttribute(AnnotationsAttribute.invisibleTag);
                    if (visibleTag != null) {
                        realSrcClass.getMethodInfo().addAttribute(visibleTag)
                        remove(it.getMethodInfo().attributes, AnnotationsAttribute.visibleTag)
                    }
                    if (invisibleTag != null) {
                        realSrcClass.getMethodInfo().addAttribute(invisibleTag)
                        remove(it.getMethodInfo().attributes, AnnotationsAttribute.invisibleTag)
                    }

                    Replace replace = (Replace)config.getAnnotation(Replace)
                    def makeClass = ProxyClassMaker.make(it, replace)
                    wetClasses.add(makeClass)

                    def body
                    def returnFlag = ''
                    if (realSrcClass.returnType != CtClass.voidType) {
                        returnFlag = "return (\$r)makeclass.getResult();"
                    }

                    if (Modifier.isStatic(realSrcClass.modifiers)) {
                        body = "$makeClass.name makeclass = new $makeClass.name(null, \$\$);" +
                                " new $config.declaringClass.name().$config.name(makeclass);" +
                                returnFlag
                    } else {
                        body = "$makeClass.name makeclass = new $makeClass.name(\$0, \$\$);" +
                                " new $config.declaringClass.name().$config.name(makeclass);" +
                                returnFlag
                    }
                    realSrcClass.setBody("{$body}")
                }
            }
        }

        return wetClasses
    }

    static synchronized void remove(List list, String name) {
        if (list == null)
            return;

        ListIterator iterator = list.listIterator();
        while (iterator.hasNext()) {
            AttributeInfo ai = (AttributeInfo)iterator.next();
            if (ai.getName().equals(name))
                iterator.remove();
        }
    }
}