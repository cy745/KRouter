package com.zhangke.krouter.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.zhangke.krouter.annotation.Destination
import com.zhangke.krouter.annotation.Param
import com.zhangke.krouter.compiler.code.buildHandleParamsFunction
import com.zhangke.krouter.compiler.code.buildParamStateClass
import com.zhangke.krouter.compiler.ext.asClassDeclaration
import com.zhangke.krouter.compiler.ext.combinations
import com.zhangke.krouter.compiler.ext.requestAnnotation
import com.zhangke.krouter.compiler.ext.requireAnnotation

/**
 * 真正实现路由注入的处理器，继承自KRouterCollectProcessor
 * 收集完所在模块后才会执行注入操作
 */
class KRouterInjectProcessor(
    environment: SymbolProcessorEnvironment
) : KRouterCollectProcessor(environment) {

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val resultList = super.process(resolver)

        // 若存在生成的文件，则说明收集到了路由信息，还会触发一次process，此时可跳过注入操作
        if (environment.codeGenerator.generatedFile.isNotEmpty()) {
            return resultList
        }

        val generatedItems = resolver.getDeclarationsFromPackage(GENERATED_SHARED_PACKAGE)
        val propertiesItems = generatedItems
            .mapNotNull { (it as? KSClassDeclaration)?.getDeclaredProperties() }
            .flatten()

        val collectedMap = propertiesItems.map { property ->
            val propertyClazz = property.type
                .resolve()
                .declaration
                .asClassDeclaration()

            propertyClazz
        }.toList()

        writeToFile(environment.codeGenerator, collectedMap)

        return resultList
    }

    private fun writeToFile(
        codeGenerator: CodeGenerator,
        collectedMap: List<KSClassDeclaration>
    ) {
        if (collectedMap.isEmpty()) return

        val codeBlock = CodeBlock.builder()
            .beginControlFlow("return when (baseRoute)")
        val usedRouter = hashSetOf<String>()

        collectedMap.forEach { clazz ->
            val parameters = clazz.primaryConstructor?.parameters
                ?: emptyList()

            val code = buildCodeBlock {
                parameters.forEach { parameter ->
                    val parameterName = parameter.routeParamName // 参数的映射名称
                    val parameterType = parameter.type.resolve() // 参数的类型

                    addStatement(
                        "val %L = params.handleParams<%T>(%S)",
                        parameterName,
                        parameterType.toClassName(),
                        parameterName,
                    )
                }

                parameters.forEach { parameter ->
                    val paramAnnotation = parameter.annotations.firstOrNull()
                    val parameterType = parameter.type.resolve() // 参数的类型
                    val parameterName = parameter.routeParamName // 参数的映射名称

                    // 是否为可空类型
                    val isNullable = parameterType.nullability != Nullability.NOT_NULL

                    // 是否必须填写的参数，其次若没有默认值，则为必填
                    val isRequired = paramAnnotation?.arguments
                        ?.firstOrNull { it.name?.asString() == "required" }
                        ?.value == true || !parameter.hasDefault

                    val flags = mutableListOf<String>()

                    flags.add("ParamState.CHECK_TYPE_FLAG")
                    if (isRequired) flags.add("ParamState.CHECK_PROVIDED_FLAG")
                    if (!isNullable) flags.add("ParamState.CHECK_IS_NOT_NULL_FLAG")

                    val flagsCode = flags.joinToString(separator = " or ")

                    addStatement("${parameterName}.checkSelf(%L)", flagsCode)
                }

                val paramsMustBeProvided = parameters
                    .filter { !it.hasDefault }

                val combinations = parameters
                    .filter { it.hasDefault }
                    .combinations()
                    .sortedByDescending { it.size }

                beginControlFlow("when")
                for (conditionParams in combinations) {
                    val targetInjectParams = paramsMustBeProvided + conditionParams

                    if (targetInjectParams.isEmpty()) {
                        beginControlFlow("else ->")
                        when (clazz.classKind) {
                            ClassKind.CLASS -> addStatement("%T()", clazz.toClassName())
                            ClassKind.OBJECT -> addStatement("%T", clazz.toClassName())
                            else -> addStatement(
                                "throw IllegalArgumentException(%S)",
                                "Unsupported class kind: ${clazz.classKind}"
                            )
                        }
                        endControlFlow()
                        continue
                    }

                    val condition = conditionParams.takeIf { it.isNotEmpty() }
                        ?.run { joinToString(separator = " && ") { "${it.routeParamName} is ParamState.Provided<*>" } }
                        ?: "else"
                    beginControlFlow("$condition ->")

                    val parameterCodeResult = targetInjectParams.joinToCode(separator = ",\n") {
                        val parameterType = it.type.resolve()
                        val isNullable = parameterType.nullability != Nullability.NOT_NULL

                        var sentence = when {
                            it in conditionParams -> "${it.name?.asString()} = ${it.routeParamName}.value as %T"
                            else -> "${it.name?.asString()} = (${it.routeParamName} as ParamState.Provided<*>).value as %T"
                        }
                        if (isNullable) {
                            sentence = sentence.replace(".value", "?.value")
                                .replace(" as ", " as? ")
                        }

                        buildCodeBlock { add(sentence, parameterType.toClassName()) }
                    }
                    addStatement("%T(%L)", clazz.toClassName(), parameterCodeResult)

                    endControlFlow()
                }
                endControlFlow()
            }

            val annotation = clazz.requireAnnotation<Destination>()
            val routers = annotation.arguments
                .firstOrNull { it.name?.asString() == "router" }
                ?.let { (it.value as? ArrayList<*>)?.filterIsInstance<String>() }
                ?: return@forEach

            // 检查是否重复定义了路由
            if (routers.any { usedRouter.contains(it) }) {
                throw IllegalArgumentException("Duplicate router: $routers")
            } else {
                usedRouter.addAll(routers)
            }

            val baseRouterCondition = routers
                .joinToString(separator = ", ") { "\"$it\"" }

            codeBlock.beginControlFlow("$baseRouterCondition -> { params ->")
                .add(code)
                .endControlFlow()
        }

        codeBlock.addStatement(
            "else -> throw IllegalArgumentException(%P)",
            "Route [\$baseRoute] Not Found."
        ).endControlFlow()

        val mapType = LambdaTypeName.get(
            receiver = null,
            returnType = Any::class.asTypeName(),
            parameters = arrayOf(
                Map::class.asClassName()
                    .parameterizedBy(
                        String::class.asTypeName(),
                        Any::class.asTypeName()
                            .copy(nullable = true)
                    )
            )
        )

        val getMapFuncSpec = FunSpec.builder("getMap")
            .addParameter("baseRoute", type = String::class)
            .returns(mapType)
            .addCode(codeBlock.build())
            .build()

        val className = "KRouterInjectMap"
        val classSpec = TypeSpec.objectBuilder(className)
            .addKdoc(CLASS_KDOC)
            .addFunction(getMapFuncSpec)
            .addType(buildParamStateClass())
            .addFunction(buildHandleParamsFunction())
            .build()

        val fileSpec = FileSpec.builder(GENERATED_SHARED_PACKAGE, className)
            .addType(classSpec)
            .indent("    ")
            .build()

        // 将涉及到的类所涉及的文件作为依赖传入，方便增量编译
        val dependencies = collectedMap
            .mapNotNull { it.containingFile }
            .distinct()
            .toTypedArray()

        kotlin.runCatching {
            fileSpec.writeTo(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(aggregating = true, *dependencies)
            )
        }
    }
}

private val routeParamsNameCache = mutableMapOf<KSValueParameter, String?>()
val KSValueParameter.routeParamName: String?
    get() = routeParamsNameCache.getOrPut(this) {
        val paramAnnotation = this.requestAnnotation<Param>()

        return paramAnnotation?.arguments
            ?.firstOrNull { it.name?.asString() == "name" }
            ?.let { it.value as? String }
            ?.takeIf(String::isNotBlank)
            ?: name?.asString()
    }
