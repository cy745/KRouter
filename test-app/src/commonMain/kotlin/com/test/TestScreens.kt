package com.test

import com.lalilu.krouter.annotation.Destination

@Destination(router = ["/test/home"])
data class HomeScreen(
    val id: String,
)

@Destination(router = ["/test/detail", "/test/detail/alt"])
data class DetailScreen(
    val albumId: String,
    val title: String? = null,
    val count: Int = 0,
)

@Destination(router = ["/test/generic"])
data class GenericScreen(
    val id: String,
    val tags: List<String> = emptyList(),
    val meta: Map<String, String> = emptyMap(),
)
