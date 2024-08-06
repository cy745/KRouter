package com.zhangke.krouter.plugin.kcp

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isAnnotation
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class KCPExtensions(
    private val logger: MessageCollector
) : IrGenerationExtension {
    companion object {
        const val TARGET_ANNOTATION = "com.zhangke.krouter.annotation.Destination"
        const val KEEPER_PACKAGE = "com.zhangke.krouter"
        const val KEEPER_CLASSNAME = "KRouterDefaultValueKeeper"
        const val KEEPER_MAP_NAME = "keeperMap"
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

        // 初次处理，收集所有目标类的默认值并缓存起来
        val maps = declarations
            .filter { it.annotations.any { ano -> ano.isAnnotation(FqName(TARGET_ANNOTATION)) } }
            .mapNotNull { collectDefaultValues(it) }
            .flatten()
            .toMap()

        val targetClassId = ClassId(FqName(KEEPER_PACKAGE), Name.identifier(KEEPER_CLASSNAME))
        val targetDefaultValueKeeper = pluginContext.referenceClass(targetClassId)
        targetDefaultValueKeeper?.let {
            it.owner.findProperty(KEEPER_MAP_NAME)
                ?: return@let

            addInitBlock(
                irClass = targetDefaultValueKeeper.owner,
                pluginContext = pluginContext,
                defaultValueMap = maps
            )
        }

        log("[KCPExtensions] generate success count: ${maps.size}")
    }

    private fun collectDefaultValues(irClass: IrClass): List<Pair<String, IrExpressionBody?>>? {
        val packageFqName = irClass.packageFqName?.asString()
            ?: return null
        val clazzName = irClass.name.asString()

        return irClass.primaryConstructor
            ?.valueParameters
            ?.mapNotNull {
                it.defaultValue ?: return@mapNotNull null

                "${packageFqName}.$clazzName\$${it.name.asString()}" to it.defaultValue
            }
    }

    private fun addInitBlock(
        irClass: IrClass,
        pluginContext: IrPluginContext,
        defaultValueMap: Map<String, IrExpressionBody?>
    ) {
        val irFactory = pluginContext.irFactory
        val irBuiltIns = pluginContext.irBuiltIns
        val mapPutFunction = irBuiltIns.mutableMapClass.owner
            .functions.single { it.name.asString() == "put" }

        val initBlock = irFactory.createAnonymousInitializer(
            startOffset = irClass.startOffset,
            endOffset = irClass.endOffset,
            origin = irClass.origin,
            symbol = IrAnonymousInitializerSymbolImpl(irClass.symbol),
            isStatic = true
        ).apply {
            parent = irClass
            body = pluginContext.createIrBuilder(irClass.symbol).irBlockBody {
                val targetProperty = irClass.findProperty(KEEPER_MAP_NAME)!!.backingField!!

                defaultValueMap.forEach { entry ->
                    +irCall(mapPutFunction.symbol).apply {
                        dispatchReceiver = irGetField(null, targetProperty)
                        putValueArgument(0, irString(entry.key))
                        putValueArgument(1, entry.value?.expression)
                    }
                }
            }
        }
        irClass.declarations.add(initBlock)
    }

    private fun IrPluginContext.createIrBuilder(symbol: IrSymbol): DeclarationIrBuilder {
        return DeclarationIrBuilder(this, symbol, symbol.owner.startOffset, symbol.owner.endOffset)
    }

    private fun IrClass.findProperty(name: String): IrProperty? {
        return declarations.filterIsInstance<IrProperty>().find { it.name.asString() == name }
    }
}