package com.test

import com.lalilu.krouter.annotation.KService

@KService
object AnalyticsService {
    fun track(event: String) = println("Track: $event")
}

@KService
class UserService {
    fun getUser(id: String) = "User:$id"
}
