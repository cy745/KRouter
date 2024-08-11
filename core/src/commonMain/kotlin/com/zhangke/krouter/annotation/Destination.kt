package com.zhangke.krouter.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Destination(
    vararg val router: String,
    val remark: String = ""
)
