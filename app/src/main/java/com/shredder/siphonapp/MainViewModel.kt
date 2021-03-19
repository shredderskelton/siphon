package com.shredder.siphonapp

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface MainViewModel {
    val text: Flow<String>
    fun reset()
}

data class State(val time: Duration)

sealed class Change {
    object Tick : Change()
    object Reset : Change()
    object Dummy : Change()
}

sealed class Action {
    object Dummy : Action()
}