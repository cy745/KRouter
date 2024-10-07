package com.zhangke.krouter.compiler


import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class KRouterInjectProcessorTest {

    @Test
    fun test() {
        val kotlinSource = listOf(
            SourceFile.kotlin("Screens.kt", TestScreen),
        )

        val result = compile(
            sourceFiles = kotlinSource,
            kspProcessors = listOf(KRouterProcessorProvider())
        )
    }

    @OptIn(ExperimentalCompilerApi::class)
    fun compile(
        sourceFiles: List<SourceFile>,
        kspProcessors: List<SymbolProcessorProvider>
    ): JvmCompilationResult {
        return KotlinCompilation().apply {
            sources = sourceFiles
            inheritClassPath = true
//            workingDir = java.io.File("/Users/miku/StudioProjects/KRouter/compiler/build/tmp")

            configureKsp(true) {
                processorOptions["kRouterType"] = "inject"

                symbolProcessorProviders.apply {
                    clear()
                    addAll(kspProcessors)
                }
            }
        }.compile()
    }
}

