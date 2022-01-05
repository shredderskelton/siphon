package com.shredder.siphonapp.composite

import com.shredder.siphonapp.User
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@ExperimentalTime
data class State(
    val time: Duration = Duration.ZERO,
    val getUsersEnabled: Boolean = true,
    val users: List<User> = emptyList()
)