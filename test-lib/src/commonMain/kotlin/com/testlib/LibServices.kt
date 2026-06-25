package com.testlib

import com.lalilu.krouter.annotation.KService

@KService
object ConfigProvider {
    val appName = "KRouterTest"
    val version = "1.0"
}

@KService
class LoggerService {
    fun log(message: String) = println("[Lib] $message")
}
