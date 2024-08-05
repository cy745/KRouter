package com.zhangke.krouter.plugin.kcp

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.util.kotlinFqName

class KCPExtensions(
    private val logger: MessageCollector
) : IrGenerationExtension {

    private fun log(message: String) {
        logger.report(CompilerMessageSeverity.INFO, message)
    }

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        log("start KCPExtensions generate")

        moduleFragment.transform(IrVisitor(logger), null)

        log("end KCPExtensions generate")
    }

    class IrVisitor(
        private val logger: MessageCollector
    ) : IrElementTransformerVoidWithContext() {
        private fun log(message: String) {
            logger.report(CompilerMessageSeverity.INFO, message)
        }

        override fun visitConstructor(declaration: IrConstructor): IrStatement {
            log("parentName: ${declaration.parent.kotlinFqName.asString()}")
            log("name: ${declaration.name.asString()}")
            val result = super.visitConstructor(declaration)

            declaration.valueParameters.forEach {
                val value = (it.defaultValue?.expression as? IrConst<*>)?.value
                log("[${it.name}]: ${value}")
            }

            return result
        }
    }
}