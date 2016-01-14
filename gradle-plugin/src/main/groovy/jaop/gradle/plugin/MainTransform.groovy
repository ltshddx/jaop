package jaop.gradle.plugin

import com.android.build.api.transform.Context
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformManager
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
import org.gradle.api.Project
import java.util.concurrent.ForkJoinPool
import java.util.stream.Stream;

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
                    ctClass != it.declaringClass
                }.forEach { config ->
                    ctClass.instrument(new ExprEditor() {
                        @Override
                        void edit(MethodCall m) throws CannotCompileException {
                            Replace replace = (Replace) config.getAnnotation(Replace)
                            if (ClassMatcher.match(m.className, m.methodName, replace)) {
                                def makeClass = ProxyClassMaker.make(m.method, replace, outDir)

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

                box.bodyConfig.stream().filter {
                    ctClass != it.declaringClass
                }.forEach { config ->
                    Stream.<CtMethod> of(ctClass.declaredMethods).parallel().filter { method ->
                        ClassMatcher.match(method.declaringClass.name, method.name, (Replace) config.getAnnotation(Replace))
                    }.forEach {
                        // fixbug aop body failed
                        CtMethod realSrcMethod = new CtMethod(it.returnType, it.name, it.parameterTypes, it.declaringClass)
                        it.setName((it.name + '_jaop_create_' + it.hashCode()).replaceAll('-', '_'))
                        realSrcMethod.setModifiers(it.modifiers)
                        it.declaringClass.addMethod(realSrcMethod)
                        it.setModifiers(Modifier.setPublic(it.modifiers))

                        // repalce annotations
                        def visibleTag = it.getMethodInfo().getAttribute(AnnotationsAttribute.visibleTag);
                        def invisibleTag = it.getMethodInfo().getAttribute(AnnotationsAttribute.invisibleTag);
                        if (visibleTag != null) {
                            realSrcMethod.getMethodInfo().addAttribute(visibleTag)
                            remove(it.getMethodInfo().attributes, AnnotationsAttribute.visibleTag)
                        }
                        if (invisibleTag != null) {
                            realSrcMethod.getMethodInfo().addAttribute(invisibleTag)
                            remove(it.getMethodInfo().attributes, AnnotationsAttribute.invisibleTag)
                        }

                        Replace replace = (Replace) config.getAnnotation(Replace)
                        def makeClass = ProxyClassMaker.make(it, replace, outDir)

                        def body
                        def returnFlag = ''
                        if (realSrcMethod.returnType != CtClass.voidType) {
                            returnFlag = "return (\$r)makeclass.getResult();"
                        }

                        if (Modifier.isStatic(realSrcMethod.modifiers)) {
                            body = "$makeClass.name makeclass = new $makeClass.name(null, \$\$);" +
                                    " new $config.declaringClass.name().$config.name(makeclass);" +
                                    returnFlag
                        } else {
                            body = "$makeClass.name makeclass = new $makeClass.name(\$0, \$\$);" +
                                    " new $config.declaringClass.name().$config.name(makeclass);" +
                                    returnFlag
                        }
                        realSrcMethod.setBody("{$body}")
                    }
                }
                ctClass.writeFile(outDir.absolutePath)
            }
        }.get()
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