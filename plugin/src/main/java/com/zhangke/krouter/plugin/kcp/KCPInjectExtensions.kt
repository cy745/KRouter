package com.zhangke.krouter.plugin.kcp

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.statements

class KCPInjectExtensions(
    private val logger: MessageCollector
) : IrGenerationExtension {
    companion object {
        const val TARGET_INJECT_CLASS = "RouterMap"
        const val TARGET_INJECT_PROPERTY = "map"
        const val TARGET_INJECT_FUNC = "TOINJECT"
    }

    private fun log(message: String) {
        logger.report(
            CompilerMessageSeverity.WARNING,
            message
        )
    }

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        // 获取所有类
        val declarations = moduleFragment.files
            .map { it.declarations }
            .flatten()
            .filterIsInstance<IrClass>()

        val targetInjectRouterMapClass = declarations
            .firstOrNull { it.name.identifier == TARGET_INJECT_CLASS }
            ?: run {
                log("[TARGET_INJECT_CLASS] $TARGET_INJECT_CLASS not found")
                return
            }

        val targetInjectRouterMapProperty = targetInjectRouterMapClass.properties
            .firstOrNull { it.name.identifier == TARGET_INJECT_PROPERTY }
            ?: run {
                log("[TARGET_INJECT_PROPERTY] $TARGET_INJECT_PROPERTY not found")
                return
            }

        /**
         *  ```kotlin
         *  val map: Map<String, (Map<String, Any>) -> Any?> = mapOf(
         *     "screen" to { parms ->
         *         val number = parms["number"] as? Int
         *             ?: throw IllegalArgumentException("No number param provided.")
         *         val title = parms["title"] as? String
         *
         *         Screen2(
         *             number = number,
         *             title = title ?: TOINJECT()
         *         )
         *     }
         * )
         *  ```
         */
        val expression = targetInjectRouterMapProperty
            .backingField
            ?.initializer
            ?.expression

        val targetInjectItem = expression?.let { it as? IrCallImpl } // [mapOf] mapOf(xxx, xxx)
            ?.valueArguments
            ?.asSequence()
            ?.flatMap {
                // mapOf 在输入多个元素时，实际调用的函数是带有vararg的那一个变形
                when (it) {
                    is IrVarargImpl -> it.elements
                    else -> listOf(it)
                }
            }
            ?.filterIsInstance<IrCallImpl>() // [to] xx to xxx -> xxx.to(xxx)
            ?.flatMap { it.valueArguments }
            ?.filterIsInstance<IrFunctionExpressionImpl>() // [lambda] (xxx) -> xxx
            ?.flatMap { it.function.body?.statements ?: emptyList() }
            ?.filterIsInstance<IrReturnImpl>()
            ?.mapNotNull { it.value as? IrConstructorCallImpl } // 构造函数
            ?.toList()

        targetInjectItem?.forEach { target ->
            target.symbol.owner.valueParameters.forEach { parameter ->
                val argument = target.getValueArgument(parameter.index)

                // 若这个参数上填的非Block，则说明无需注入
                val block = argument?.let { it as? IrBlockImpl }
                    ?: return@forEach

                block.statements
                    .asSequence()
                    .filterIsInstance<IrWhenImpl>()
                    .flatMap { it.branches }
                    .forEach {
                        if (checkToInject(it.result) && parameter.defaultValue != null) {
                            it.result = parameter.defaultValue?.expression!!

                            log("[branch injectd]: ${parameter.name.asString()}")
                        }
                    }
            }
        }
    }

    private fun checkToInject(item: IrExpression): Boolean {
        return item is IrCallImpl && item.symbol.owner.name.identifier == TARGET_INJECT_FUNC
    }
}