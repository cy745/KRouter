package com.lalilu.krouter.compiler.code

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.toClassName

/**
 * 生成 [services] 属性，类型为 `List<Any>`：
 * ```
 * override val services: List<Any> by lazy {
 *     listOf(
 *         SingletonService,   // @KService object → 直接引用单例
 *         ClassService(),     // @KService class → new 实例
 *     )
 * }
 * ```
 *
 * @param services 收集到的 @KService 类列表。空列表时生成为 `emptyList()`。
 */
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
        .addModifiers(KModifier.OVERRIDE)
        .delegate(delegateBlock)
        .build()
}