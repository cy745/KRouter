package com.lalilu.krouter

interface InjectMap {
    val services: List<Any>

    fun getMap(baseRoute: String): (Map<String, Any?>) -> Any
}
