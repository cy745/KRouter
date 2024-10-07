package com.zhangke.krouter

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry

/**
 * SubType of it **MUST** be annotation with [kotlinx.serialization.Serializable]
 */
interface Screen {
    @Composable
    fun AnimatedContentScope.content(backStackEntry: NavBackStackEntry)
}