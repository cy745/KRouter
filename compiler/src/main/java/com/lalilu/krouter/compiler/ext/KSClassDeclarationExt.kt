package com.lalilu.krouter.compiler.ext

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation

internal inline fun <reified T> KSAnnotated.requireAnnotation(): KSAnnotation =
    annotations.first {
        it.typeDeclaration
            .asClassDeclaration()
            .qualifiedName
            ?.asString() == T::class.qualifiedName
    }

internal inline fun <reified T> KSAnnotated.requestAnnotation(): KSAnnotation? =
    annotations.firstOrNull {
        it.typeDeclaration
            .asClassDeclaration()
            .qualifiedName
            ?.asString() == T::class.qualifiedName
    }
