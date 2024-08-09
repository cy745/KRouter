package com.zhangke.krouter.plugin.kcp

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrConstructorImpl
import org.jetbrains.kotlin.ir.declarations.lazy.IrLazyConstructor
import org.jetbrains.kotlin.ir.declarations.lazy.IrLazyValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrIfThenElseImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.statements

class KCPInjectExtensions(
    private val logger: MessageCollector
) : IrGenerationExtension {
    companion object {
        const val TARGET_INJECT_CLASS = "KRouterInjectMap"
        const val TARGET_INJECT_FUNC = "getMap"
        const val TARGET_REPLACE_FUNC = "TOINJECT"
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

        val targetInjectRouterMapFunc = targetInjectRouterMapClass.functions
            .firstOrNull { it.name.identifier == TARGET_INJECT_FUNC }
            ?: run {
                log("[TARGET_INJECT_FUNC] $TARGET_INJECT_FUNC not found")
                return
            }

        val targetInjectItem = targetInjectRouterMapFunc.body
            ?.statements
            ?.asSequence()
            ?.filterIsInstance<IrReturnImpl>()
            ?.mapNotNull { it.value as? IrBlockImpl }
            ?.flatMap { it.statements }
            ?.filterIsInstance<IrWhenImpl>()
            ?.flatMap { it.branches }
            ?.filterIsInstance<IrBranchImpl>()
            ?.map { it.result }
            ?.filterIsInstance<IrFunctionExpressionImpl>() // [lambda] (xxx) -> xxx
            ?.flatMap { it.function.body?.statements ?: emptyList() }
            ?.filterIsInstance<IrReturnImpl>()
            ?.mapNotNull { it.value as? IrConstructorCallImpl } // 构造函数
            ?.toList()
            ?: emptyList()

        for (target in targetInjectItem) {
            log("[target]: $target")
            pluginContext.tryInject(target)
        }
    }

    private fun IrPluginContext.tryInject(target: IrConstructorCallImpl) {
        when (val constructor = target.symbol.owner) {
            is IrConstructorImpl -> handleInject(target, constructor)
            is IrLazyConstructor -> handleLazyInject(target, constructor)
            else -> log("[constructor] $constructor")
        }
    }

    private fun IrPluginContext.handleInject(
        target: IrConstructorCallImpl,
        constructorImpl: IrConstructorImpl,
    ) {
        val parameters = constructorImpl.valueParameters
        for (parameter in parameters) {
            val argument = target.getValueArgument(parameter.index)

            // 若这个参数上填的非Block，则说明无需注入
            val block = argument?.let { it as? IrBlockImpl }
                ?: continue

            val branches = block.statements.run {
                filterIsInstance<IrWhenImpl>()
                    .flatMap { it.branches }
                    .toList() + filterIsInstance<IrIfThenElseImpl>()
                    .flatMap { it.branches }
            }

            branches.forEach {
                if (checkToInject(it.result) && parameter.defaultValue != null) {
                    it.result = parameter.defaultValue?.expression!!

                    log("[branch injectd]: ${parameter.name.asString()}")
                }
            }
        }
    }

    private fun IrPluginContext.handleLazyInject(
        target: IrConstructorCallImpl,
        constructorLazy: IrLazyConstructor,
    ) {
        val parameters = constructorLazy.valueParameters
            .filterIsInstance<IrLazyValueParameter>()

        for (parameter in parameters) {
            val argument = target.getValueArgument(parameter.index)

            // 若这个参数上填的非Block，则说明无需注入
            val block = argument?.let { it as? IrBlockImpl }
                ?: continue

            val branches = block.statements.run {
                filterIsInstance<IrWhenImpl>()
                    .flatMap { it.branches }
                    .toList() + filterIsInstance<IrIfThenElseImpl>()
                    .flatMap { it.branches }
            }

            branches.filterIsInstance<IrBranchImpl>()
                .forEach {
                    if (checkToInject(it.result)) {

                    }
                }
        }
    }

    private fun checkToInject(item: IrExpression): Boolean {
        return item is IrCallImpl && item.symbol.owner.name.identifier == TARGET_REPLACE_FUNC
    }
}