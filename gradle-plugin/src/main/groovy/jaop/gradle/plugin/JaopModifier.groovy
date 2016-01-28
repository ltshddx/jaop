package jaop.gradle.plugin

import javassist.CannotCompileException
import javassist.ClassPool
import javassist.CtClass
import javassist.CtField
import javassist.CtMethod
import javassist.Modifier
import javassist.NotFoundException
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.AttributeInfo
import javassist.expr.ExprEditor
import javassist.expr.MethodCall

class JaopModifier {
    static List<CtMethod> callReplace(CtClass ctClass, Config config, File outDir) {
        ctClass.instrument(new ExprEditor() {
            @Override
            void edit(MethodCall m) throws CannotCompileException {
                if (ClassMatcher.match(m, config.target)) {
                    def makeClass
                    def body = ''

                    if (m.isSuper()) {
                        // 如果要hook一个 super. 的方法，不能用常规方法，先将这个super. 封装成其他方法
                        // 然后就当这里是那个封装的方法
                        def methodName = "${m.methodName}_from_super_for_jaop_${m.method.hashCode()}"
                        CtMethod superMethod
                        try {
                            superMethod = m.enclosingClass.getDeclaredMethod(methodName, m.method.parameterTypes)
                        } catch (NotFoundException e) {
                            superMethod = new CtMethod(m.method.returnType,
                                    methodName,
                                    m.method.parameterTypes, m.enclosingClass)
                            if (superMethod.returnType == CtClass.voidType) {
                                superMethod.setBody("{super.$m.methodName(\$\$);}")
                            } else {
                                superMethod.setBody("{return super.$m.methodName(\$\$);}")
                            }
                            m.enclosingClass.addMethod(superMethod)
                        }

                        makeClass = ProxyClassMaker.make(superMethod, outDir)
                        body = "$makeClass.name makeclass = new $makeClass.name(this, \$\$);"
                    } else {
                        makeClass = ProxyClassMaker.make(m.method, outDir)
                        body += "$makeClass.name makeclass = new $makeClass.name(\$0, \$\$);"
                    }

                    // 静态方法 没有this
                    if (!m.withinStatic()) {
                        body += 'makeclass.callThis = this;'
                    }
                    body += " new $config.ctMethod.declaringClass.name().$config.ctMethod.name((${config.ctMethod.parameterTypes[0].name})makeclass);" +
                            "\$_ = (\$r)makeclass.result;"
                    m.replace(body)
                }
            }
        })
    }

    static List<CtMethod> bodyReplace(CtMethod it, Config config, File outDir) {
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

        def makeClass = ProxyClassMaker.make(it, outDir)

        def body
        def returnFlag = ''
        if (realSrcMethod.returnType != CtClass.voidType) {
            returnFlag = 'return'
        }

        if (Modifier.isStatic(realSrcMethod.modifiers)) {
            body = "$makeClass.name makeclass = new $makeClass.name(null, \$\$);"
        } else {
            body = "$makeClass.name makeclass = new $makeClass.name(\$0, \$\$);"
        }
        body += " new $config.ctMethod.declaringClass.name().$config.ctMethod.name((${config.ctMethod.parameterTypes[0].name})makeclass);" +
                (returnFlag == '' ? '' : "return (\$r)makeclass.result;")

        if (config.target.handleSubClass) {
            def cflowField = getCflowField(it.declaringClass.classPool, config)
            body = """
                try {
                    int value = ${cflowField}.value();
                    ${cflowField}.enter();
                    if (value == 0) {
                        $body
                    } else {
                        $returnFlag $it.name(\$\$);
                    }
                } finally {
                    ${cflowField}.exit();
                }
                """
        }
        realSrcMethod.setBody("{$body}")
    }

    static List<CtMethod> callBefore(CtClass ctClass, Config config, File outDir) {
        ctClass.instrument(new ExprEditor() {
            @Override
            void edit(MethodCall m) throws CannotCompileException {
                if (ClassMatcher.match(m, config.target)) {
                    def body = "jaop.domain.internal.HookImplForPlugin hook = new jaop.domain.internal.HookImplForPlugin();"
                    // 静态方法 没有this
                    if (!m.withinStatic()) {
                        body += 'hook.callThis = this;'
                    }
                    body += "hook.target = \$0;"
                    body += "hook.args = \$args;"
                    body += "new $config.ctMethod.declaringClass.name().$config.ctMethod.name((${config.ctMethod.parameterTypes[0].name})hook);"
                    body += '$_ = $proceed($$);'
                    m.replace(body)
                }
            }
        })
    }

    static List<CtMethod> bodyBefore(CtMethod ctMethod, Config config, File outDir) {
        def body = "jaop.domain.internal.HookImplForPlugin hook = new jaop.domain.internal.HookImplForPlugin();"
        if (!Modifier.isStatic(ctMethod.modifiers)) {
            body += "hook.target = \$0;"
        }
        body += "hook.args = \$args;"
        body += "new $config.ctMethod.declaringClass.name().$config.ctMethod.name((${config.ctMethod.parameterTypes[0].name})hook);"
        if (config.target.handleSubClass) {
            body = cflow(ctMethod, body, config)
        }
        ctMethod.insertBefore(body)
    }

    static List<CtMethod> callAfter(CtClass ctClass, Config config, File outDir) {
        ctClass.instrument(new ExprEditor() {
            @Override
            void edit(MethodCall m) throws CannotCompileException {
                if (ClassMatcher.match(m, config.target)) {
                    def body = '$_ = $proceed($$);'
                    body += "jaop.domain.internal.HookImplForPlugin hook = new jaop.domain.internal.HookImplForPlugin();"
                    // 静态方法 没有this
                    if (!m.withinStatic()) {
                        body += 'hook.callThis = this;'
                    }
                    body += "hook.target = \$0;"
                    body += "hook.result = (\$w)\$_;"
                    body += "hook.args = \$args;"
                    body += "new $config.ctMethod.declaringClass.name().$config.ctMethod.name((${config.ctMethod.parameterTypes[0].name})hook);"
                    body += "\$_ = (\$r)hook.result;"
                    m.replace(body)
                }
            }
        })
    }

    static List<CtMethod> bodyAfter(CtMethod ctMethod, Config config, File outDir) {
        def body = "jaop.domain.internal.HookImplForPlugin hook = new jaop.domain.internal.HookImplForPlugin();"
        if (!Modifier.isStatic(ctMethod.modifiers)) {
            body += "hook.target = \$0;"
        }
        body += "hook.result = (\$w)\$_;"
        body += "hook.args = \$args;"
        body += "new $config.ctMethod.declaringClass.name().$config.ctMethod.name((${config.ctMethod.parameterTypes[0].name})hook);"
        body += "\$_ = (\$r)hook.result;"
        if (config.target.handleSubClass) {
            body = cflow(ctMethod, body, config)
        }
        ctMethod.insertAfter(body)
    }

    static void bodyBeforeAndAfter(CtMethod ctMethod, Config before, Config after, File outDir) {
        // setbody 没有 $proceed 方法  略微蛋疼
//        def body = "jaop.domain.internal.HookImplForPlugin hook = new jaop.domain.internal.HookImplForPlugin();"
//        body += "hook.target = \$0;"
//        body += "hook.args = \$args;"
//
//        body += "$before.ctMethod.declaringClass.name jaop = new $before.ctMethod.declaringClass.name();"
//        body += "jaop.$before.ctMethod.name(hook);"
//        body += 'hook.result = ($w)$proceed($$);'
//        if (!before.ctMethod.declaringClass.name.equals(after.ctMethod.declaringClass.name)) {
//            body += "$after.ctMethod.declaringClass.name jaop2 = new $after.ctMethod.declaringClass.name();"
//        } else {
//            body += "$after.ctMethod.declaringClass.name jaop2 = aop;"
//        }
//        body += "jaop2.$after.ctMethod.name(hook);"
//        body += "return (\$r)hook.result;"
//        ctMethod.setBody("{$body}", ctMethod.declaringClass.name, ctMethod.name)

        bodyBefore(ctMethod, before, outDir)
        bodyAfter(ctMethod, after, outDir)
    }

    static String cflow(CtMethod ctMethod, String body, Config config) {
        def fname = getCflowField(ctMethod.declaringClass.classPool, config)
        ctMethod.insertBefore(fname + ".enter();");
        String src = fname + ".exit();";
        ctMethod.insertAfter(src, true);
        return "if (${fname}.value() == 0) {$body}"
    }

    static synchronized String getCflowField(ClassPool classPool, Config config) {
        def cflowFlag = config.target.value + config.annotation.getClass().getName()
        Object[] cflow = classPool.lookupCflow(cflowFlag)
        def fname
        if (cflow == null) {
            CtClass cc = classPool.get("jaop.domain.internal.Cflow");
            cc.checkModify();
            int i = 0;
            while (true) {
                fname = '_cflow$' + i++;
                try {
                    cc.getDeclaredField(fname);
                }
                catch(NotFoundException e) {
                    break;
                }
            }

            classPool.recordCflow(cflowFlag, "jaop.domain.internal.Cflow", fname);
            try {
                CtClass type = cc;
                CtField field = new CtField(type, fname, cc);
                field.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
                cc.addField(field, CtField.Initializer.byNew(type));
                fname = "jaop.domain.internal.Cflow." + fname
                return fname
            }
            catch (NotFoundException e) {
                throw new CannotCompileException(e);
            }
        } else {
            fname = cflow[0].toString() + '.' + cflow[1].toString()
        }

        return fname
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