package com.zhangke.krouter.plugin.kcp

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CommandLineProcessor::class)
class KCPCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String
        get() = "plugin"
    override val pluginOptions: Collection<AbstractCliOption>
        get() = emptyList()
}