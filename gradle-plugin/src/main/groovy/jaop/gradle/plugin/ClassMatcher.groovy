package  jaop.gradle.plugin

import jaop.domain.annotation.Replace

class ClassMatcher {

    static boolean match(String clazz, String methodName, Replace replace) {
        def match = replace.value().trim()
        match = match.replace('**', ".+")
        match = match.replace('*', '\\w+')

        // 利用正则
        def result = "$clazz.$methodName" ==~ match
//        if (result) {
//            println "[matcher] $clazz.$methodName is match ${replace.value()}"
//        }

        return result
    }
}