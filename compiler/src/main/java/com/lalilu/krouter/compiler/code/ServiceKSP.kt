package com.lalilu.krouter.compiler.code

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.toClassName

fun handleServicesProperties(services: List<KSClassDeclaration>): PropertySpec {
    val listType = List::class.asClassName().parameterizedBy(Any::class.asTypeName())

    val delegateBlock = buildCodeBlock {
        beginControlFlow("lazy")
        if (services.isEmpty()) {
            addStatement("emptyList()")
        } else {
            addStatement("listOf(")
            indent()
            services.forEachIndexed { index, clazz ->
                val className = clazz.toClassName()
                val isLast = index == services.size - 1
                if (clazz.classKind == ClassKind.OBJECT) {
                    if (isLast) addStatement("%T", className)
                    else addStatement("%T,", className)
                } else {
                    if (isLast) addStatement("%T()", className)
                    else addStatement("%T(),", className)
                }
            }
            unindent()
            addStatement(")")
        }
        endControlFlow()
    }

    return PropertySpec.builder("services", listType)
        .delegate(delegateBlock)
        .build()
}