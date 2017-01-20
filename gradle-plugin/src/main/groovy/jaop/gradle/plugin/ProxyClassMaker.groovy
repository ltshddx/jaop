//package jaop.gradle.plugin
//
//import jaop.domain.internal.HookImplForPlugin
//import javassist.CtBehavior
//import javassist.CtClass
//import javassist.CtConstructor
//import javassist.CtField
//import javassist.CtMethod
//import javassist.Modifier
//
//class ProxyClassMaker {
//
//    static def make(CtBehavior ctMethod, List<CtClass> wetClasses) {
//        def classPool = ctMethod.declaringClass.classPool
//        String className = ctMethod.declaringClass.name + '$Proxy_' + ctMethod.hashCode()
//        className = className.replace('-', '_')
//        synchronized (ctMethod) {
//            def makeClass = classPool.getOrNull(className)
//            if (makeClass == null) {
//                makeClass = classPool.makeClass(className)
//                makeClass.setSuperclass(classPool.get(HookImplForPlugin.class.name))
//
//                def ctConstructor = new CtConstructor(new CtClass[0], makeClass)
//                ctConstructor.setBody("{}")
//                makeClass.addConstructor(ctConstructor)
//
//                // create process method
//                CtMethod process = new CtMethod(classPool.get(Object.class.name), 'process', null, makeClass)
//                def returnBodyPrefix = ''
//                if (ctMethod instanceof CtConstructor || ((CtMethod)ctMethod).returnType != CtClass.voidType) {
//                    // 基本数据类型在转化object时  dex工具会出错
//                    returnBodyPrefix = "result = (\$w)"
//                }
//
//                def paramBody = ''
//                ctMethod.parameterTypes.eachWithIndex { CtClass entry, int i ->
//                    paramBody += getBasicType(entry, "args[$i]") + ","
//                }
//                if (paramBody != '') {
//                    paramBody = paramBody.substring(0, paramBody.length() - 1)
//                }
//
//                if (Modifier.isStatic(ctMethod.modifiers)) {
//                    process.setBody("{$returnBodyPrefix $ctMethod.declaringClass.name.$ctMethod.name($paramBody);return result;}")
//                } else if (ctMethod instanceof CtConstructor) {
//                    process.setBody("{$returnBodyPrefix new $ctMethod.declaringClass.name($paramBody); return result;}")
//                } else {
//                    process.setBody("{$returnBodyPrefix (($ctMethod.declaringClass.name)target).$ctMethod.name($paramBody); return result;}")
//                }
//                def throwable = classPool.get('java.lang.Throwable') as CtClass[]
//                process.setExceptionTypes(throwable)
//                makeClass.addMethod(process)
//
//                makeClass.freeze()
//                wetClasses.add(makeClass)
//                MainTransform.logger.info "make proxy class for $ctMethod.declaringClass.name.$ctMethod.name, name is $makeClass.name"
//            }
//            return makeClass
//        }
//    }
//
//    static String getBasicType(CtClass ctClass, String param) {
//        switch (ctClass.name) {
//            case 'boolean':
//                return "((Boolean)$param).booleanValue()"
//            case 'char':
//                return "((Character)$param).charValue()"
//            case 'byte':
//                return "((Byte)$param).byteValue()"
//            case 'short':
//                return "((Short)$param).shortValue()"
//            case 'int':
//                return "((Integer)$param).intValue()"
//            case 'long':
//                return "((Long)$param).longValue()"
//            case 'float':
//                return "((Float)$param).floatValue()"
//            case 'double':
//                return "((Double)$param).doubleValue()"
//            default:
//                return "($ctClass.name)$param"
//        }
//    }
//}