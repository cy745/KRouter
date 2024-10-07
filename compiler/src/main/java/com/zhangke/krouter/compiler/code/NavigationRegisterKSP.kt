package com.zhangke.krouter.compiler.code

import androidx.navigation.NavGraphBuilder
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.toClassName
import com.zhangke.krouter.annotation.DefaultNavConfig
import com.zhangke.krouter.annotation.NavConfig


private val composableFunc = ClassName("androidx.navigation.compose", "composable")
private val toRouteFunc = ClassName("androidx.navigation", "toRoute")

@OptIn(KspExperimental::class)
fun buildNavigationRegisterKSP(
    resolver: Resolver,
    collectedMap: List<KSClassDeclaration>,
): FunSpec {
    val codeBlock = CodeBlock.builder()
    val defaultNavConfigClazz = DefaultNavConfig::class.qualifiedName
        ?.let { resolver.getClassDeclarationByName(it) }

    collectedMap.forEach { clazz ->
        val navConfig = clazz.getAnnotationsByType(NavConfig::class)
            .firstOrNull()?.clazz
            ?.let {
                // 通过全限定名称获取元素的ClassDeclaration
                val qualifyName = it.asClassName().canonicalName
                resolver.getClassDeclarationByName(qualifyName)
            }

        if (navConfig != null) {
            // 若存在自定义配置，则校验继承关系
            require(navConfig.superTypes.any {
                it.resolve().toClassName().canonicalName == DefaultNavConfig::class.qualifiedName
            }) { "@NavConfig value must extends DefaultNavConfig" }

            val navConfigNameForClazz = "configFor${clazz.toClassName().simpleName}"
            val clazzType = if (navConfig.classKind == ClassKind.OBJECT) "%T" else "%T()"

            codeBlock.addStatement(
                "val $navConfigNameForClazz = $clazzType",
                navConfig.toClassName()
            ).beginControlFlow(
                "%T<%T>(\n%L) {",
                composableFunc,
                clazz.toClassName(),
                buildCodeBlock {
                    defaultNavConfigClazz?.getAllProperties()?.forEach {
                        val property = it.simpleName.asString()
                        addStatement("$property = %N.$property,", navConfigNameForClazz)
                    }
                }
            )
        } else {
            codeBlock.beginControlFlow(
                "%T<%T> {",
                composableFunc,
                clazz.toClassName()
            )
        }

        codeBlock.addStatement(
            "val screen = it.%T<%T>()",
            toRouteFunc,
            clazz.toClassName()
        )
            .addStatement("with(screen) { content(it) }")
            .endControlFlow()
    }

    return FunSpec.builder("register")
        .addCode(codeBlock.build())
        .receiver(NavGraphBuilder::class)
        .build()
}