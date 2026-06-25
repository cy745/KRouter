package com.testlib

import com.lalilu.krouter.annotation.Destination

@Destination(router = ["/lib/profile"])
data class ProfileScreen(
    val userId: String,
    val nickname: String = "",
)

@Destination(router = ["/lib/settings", "/lib/prefs"])
data class SettingsScreen(
    val theme: String = "light",
)
