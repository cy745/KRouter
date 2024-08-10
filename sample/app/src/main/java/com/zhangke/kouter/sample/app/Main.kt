package com.zhangke.kouter.sample.app

import com.zhangke.krouter.KRouter
import com.zhangke.krouter.generated.KRouterInjectMap
import com.zhangke.krouter.sample.core.Screen
import kotlin.system.measureTimeMillis

fun main() {
    val initCost = measureTimeMillis {
        KRouter.init()
    }
    println("init cost: $initCost")

    val debugScreenCost = measureTimeMillis {
        val screen = KRouterInjectMap.getMap("screen/test")(mapOf("title" to "4500"))
        (screen as? Screen)?.content()
    }
    println("DebugScreen cost: $debugScreenCost")

    val cost = measureTimeMillis {
        KRouter.route<Screen>("screen/home") {
            with("name", "10")
            with("list", listOf("1", "2", "3", "${System.currentTimeMillis()}"))
        }?.content()
    }
    println("cost: $cost")

    KRouter.route<Screen>("screen/home/detail")?.content()
    KRouter.route<Screen>("screen/home/second")?.content()

    KRouter.route<Screen>("screen/home/landing")?.content()
    KRouter.route<Screen>("krouter://sample.com/screen/home?name=zhangke")?.content()
    KRouter.route<Screen>("screen/home/detail?name=zhangke")?.content()
    KRouter.route<Screen>("screen/profile?name=zhangke")?.content()
    KRouter.route<Screen>("screen/profile/detail?name=zhangke")?.content()
    KRouter.route<Screen>("screen/setting")?.content()
    KRouter.route<Screen>("screen/setting/detail")?.content()
}
