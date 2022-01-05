package com.shredder.siphonapp

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

interface MainViewModel {
    val text: Flow<String>
    val getUsersEnabled: Flow<Boolean>
    val users: Flow<List<String>>
    fun reset()
    fun getUsers()
}

@ExperimentalTime
data class State(
    val time: Duration = Duration.ZERO,
    val getUsersEnabled: Boolean = true,
    val users: List<User> = emptyList()
)
