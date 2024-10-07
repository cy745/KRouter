package com.zhangke.krouter.compiler

import com.tschuchort.compiletesting.SourceFile


const val TestScreen = """
package com.zhangke.krouter.test

import com.zhangke.krouter.annotation.Destination
import com.zhangke.krouter.annotation.Param
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import com.zhangke.krouter.Screen
import com.zhangke.krouter.annotation.Destination

interface Screen
interface TabScreen

@Destination("screen/test", "screen/test2")
data class TestScreen(
    val title: String,
    val name: String = ""
) : Screen {
    @Composable
    override fun AnimatedContentScope.content(backStackEntry: NavBackStackEntry) {

    }
}

@Destination("screen/main")
class MainScreen(
    val number: Int = 0
) : Screen {
    @Composable
    override fun AnimatedContentScope.content(backStackEntry: NavBackStackEntry) {
    }
}

@Destination("screen/settings")
class SettingsScreen(
    @Param(required = true)
    val index: Int? = 0,
    val count: Int = 10,
    val price: Int
): Screen {
    @Composable
    override fun AnimatedContentScope.content(backStackEntry: NavBackStackEntry) {
    }
}
"""

private fun test() {
    SourceFile.kotlin("Screens.kt", TestScreen)
}