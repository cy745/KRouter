package com.lalilu.krouter.annotation


@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class Param(
    val name: String = "",
    val remark: String = "",
    val required: Boolean = false
)