package com.test

import com.lalilu.krouter.InjectMap
import com.lalilu.krouter.annotation.KInject

@KInject
expect fun kRouterInjectMap(): InjectMap
