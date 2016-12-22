package jaop.gradle.plugin

import javassist.CannotCompileException
import javassist.ClassPool
import javassist.CtBehavior
import javassist.CtClass
import javassist.CtConstructor
import javassist.CtField
import javassist.CtMethod
import javassist.Modifier
import javassist.NotFoundException
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.AttributeInfo
import javassist.expr.Expr
import javassist.expr.MethodCall
import javassist.expr.NewExpr

class JaopModifier {
    static void callReplaceForConstructor(NewExpr e, Config config, List<CtClass> wetClasses) {
        def makeClass = ProxyClassMaker.make(e.constructor, wetClasses)
        def body = "$makeClass.name makeclass = new $makeClass.name();"
        body += 'makeclass.target = $0;'
        body += 'makeclass.args = $args;'

        // 静态方法 没有this
        if (!e.withinStatic()) {
            body += 'makeclass.callThis = this;'
        }
        body += " new $config.ctMethod.declaringClass.name().$config.ctMethod.name((${config.ctMethod.parameterTypes[0].name})makeclass);" +
                "\$_ = (\$r)makeclass.result;"
        e.replace(body)
    }

    static void callReplace(MethodCall m, Config config, List<CtClass> wetClasses) {
        def makeClass
        def body = ''

        if (m.isSuper()) {
            // 如果要hook一个 super. 的方法，不能用常规方法，先将这个super. 封装成其他方法
            // 然后就当这里是那个封装的方法
            def methodName = "${m.methodName}_from_super_for_jaop_${m.method.hashCode()}".replace('-', '_')
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

            makeClass = ProxyClassMaker.make(superMethod, wetClasses)
            body = "$makeClass.name makeclass = new $makeClass.name();"
            body += 'makeclass.target = this;'
        } else {
            makeClass = ProxyClassMaker.make(m.method, wetClasses)
            body += "$makeClass.name makeclass = new $makeClass.name();"
            body += 'makeclass.target = $0;'
        }
        body += 'makeclass.args = $args;'

        // 静态方法 没有this
        if (!m.withinStatic()) {
            body += 'makeclass.callThis = this;'
        }
        body += " new $config.ctMethod.declaringClass.name().$config.ctMethod.name((${config.ctMethod.parameterTypes[0].name})makeclass);" +
                "\$_ = (\$r)makeclass.result;"
        m.replace(body)
    }

    static void bodyReplaceForConstructor(CtConstructor it, Config config, List<CtClass> wetClasses) {
        def toMethod = it.toMethod((it.name + '_jaop_constructor_' + it.hashCode()).replaceAll('-', '_'), it.declaringClass)
        it.declaringClass.addMethod(toMethod)
        // remove annotations
        remove(toMethod.getMethodInfo().attributes, AnnotationsAttribute.visibleTag)
        remove(toMethod.getMethodInfo().attributes, AnnotationsAttribute.invisibleTag)

        def makeClass = ProxyClassMaker.make(toMethod, wetClasses)

        def body = "$makeClass.name makeclass = new $makeClass.name();"
        body += 'makeclass.target = $0;'
        body += 'makeclass.args = $args;'
        body += " new $config.ctMethod.declaringClass.name().$config.ctMethod.name((${config.ctMethod.parameterTypes[0].name})makeclass);"

        if (config.target.handleSubClass) {
            def cflowField = getCflowField(it.declaringClass.classPool, config, wetClasses)
            body = """
                try {
                    int value = ${cflowField}.value();
                    ${cflowField}.enter();
                    if (value == 0) {
                        $body
                    } else {
                        $toMethod.name(\$\$);
                    }
                } finally {
                    ${cflowField}.exit();
                }
                """
        }
        it.insertBeforeBody(body + 'return;')
    }

    static void bodyReplace(CtMethod it, Config config, List<CtClass> wetClasses) {
        // fixbug aop body failed
        CtMethod realSrcMethod = new CtMethod(it.returnType, it.name, it.parameterTypes, it.declaringClass)
        it.setName((it.name + '_jaop_method_' + it.declaringClass.hashCode() + '_' + it.hashCode()).replaceAll('-', '_'))
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

        def makeClass = ProxyClassMaker.make(it, wetClasses)

        def body
        def returnFlag = ''
        if (realSrcMethod.returnType != CtClass.voidType) {
            returnFlag = 'return'
        }

        body = "$makeClass.name makeclass = new $makeClass.name();"
        if (!Modifier.isStatic(realSrcMethod.modifiers)) {
            body += 'makeclass.target = $0;'
        }
        body += 'makeclass.args = $args;'
        body += " new $config.ctMethod.declaringClass.name().$config.ctMethod.name((${config.ctMethod.parameterTypes[0].name})makeclass);" +
                (returnFlag == '' ? '' : "return (\$r)makeclass.result;")

        if (config.target.handleSubClass) {
            def cflowField = getCflowField(it.declaringClass.classPool, config, wetClasses)
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

    static void callBefore(Expr m, Config config, List<CtClass> wetClasses) {
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

    static void bodyBefore(CtBehavior ctBehavior, Config config, List<CtClass> wetClasses) {
        def body = "jaop.domain.internal.HookImplForPlugin hook = new jaop.domain.internal.HookImplForPlugin();"
        if (!Modifier.isStatic(ctBehavior.modifiers)) {
            body += "hook.target = \$0;"
        }
        body += "hook.args = \$args;"
        body += "new $config.ctMethod.declaringClass.name().$config.ctMethod.name((${config.ctMethod.parameterTypes[0].name})hook);"
        if (config.target.handleSubClass) {
            body = cflow(ctBehavior, body, config, wetClasses)
        }
        ctBehavior.insertBefore(body)
    }

    static void callAfter(Expr m, Config config, List<CtClass> wetClasses) {
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

    static void bodyAfter(CtBehavior ctBehavior, Config config, List<CtClass> wetClasses) {
        def body = "jaop.domain.internal.HookImplForPlugin hook = new jaop.domain.internal.HookImplForPlugin();"
        if (!Modifier.isStatic(ctBehavior.modifiers)) {
            body += "hook.target = \$0;"
        }
        body += "hook.result = (\$w)\$_;"
        body += "hook.args = \$args;"
        body += "new $config.ctMethod.declaringClass.name().$config.ctMethod.name((${config.ctMethod.parameterTypes[0].name})hook);"
        body += "\$_ = (\$r)hook.result;"
        if (config.target.handleSubClass) {
            body = cflow(ctBehavior, body, config, wetClasses)
        }
        ctBehavior.insertAfter(body)
    }

    static void bodyBeforeAndAfter(CtMethod ctMethod, Config before, Config after, List<CtClass> wetClasses) {
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

        bodyBefore(ctMethod, before, wetClasses)
        bodyAfter(ctMethod, after, wetClasses)
    }

    static String cflow(CtBehavior ctBehavior, String body, Config config, List<CtClass> wetClasses) {
        def fname = getCflowField(ctBehavior.declaringClass.classPool, config, wetClasses)
        ctBehavior.insertBefore(fname + ".enter();");
        String src = fname + ".exit();";
        ctBehavior.insertAfter(src, true);
        return "if (${fname}.value() == 0) {$body}"
    }

    static synchronized String getCflowField(ClassPool classPool, Config config, List<CtClass> wetClasses) {
        def cflowImpl = 'jaop.domain.internal.Cflow$Impl'
        def cflowFlag = config.target.value + config.annotation.getClass().getName()
        Object[] cflow = classPool.lookupCflow(cflowFlag)
        def fname
        if (cflow == null) {
            CtClass cc = classPool.getOrNull(cflowImpl)
            if (cc == null) {
                cc = classPool.makeClass(cflowImpl)
                wetClasses.add(cc)
            }
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

            classPool.recordCflow(cflowFlag, cflowImpl, fname);
            try {
                CtClass type = classPool.getOrNull("jaop.domain.internal.Cflow");
                CtField field = new CtField(type, fname, cc);
                field.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
                cc.addField(field, CtField.Initializer.byNew(type));
                fname = "$cflowImpl.$fname"
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