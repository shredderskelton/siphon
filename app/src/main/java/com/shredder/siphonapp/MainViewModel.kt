package com.shredder.siphonapp

import com.shredder.siphon.Effect
import com.shredder.siphon.Siphon
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.isActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

interface MainViewModel {
    val text: Flow<String>
    fun reset()
}

@ExperimentalTime
class MainViewModelImpl() : MainViewModel {

    private val siphon = Siphon<State, Change, Action>(
        initialState = State(time = Duration.ZERO),
        reducer = { state, change ->
            when (change) {
                Change.Dummy -> Effect(state, listOf(Action.Dummy))
                Change.Tick -> Effect(
                    state.copy(time = state.time.plus(1.seconds)),
                    listOf(Action.Dummy)
                )
                Change.Reset -> Effect(State(time = Duration.ZERO), listOf(Action.Dummy))
            }
        },
        actions = { action ->
            when (action) {
                Action.Dummy -> flowOf(Change.Dummy)
            }
        },
        events = listOf(
            flow {
                while (currentCoroutineContext().isActive) {
                    delay(1000)
                    emit(Change.Tick)
                }
            },
        )
    )

    override fun reset() {
        siphon.change(Change.Reset)
    }

    override val text: Flow<String> = siphon.state.map { it.time.toIsoString() }
}

data class State @OptIn(ExperimentalTime::class) constructor(val time: Duration)

sealed class Change {
    object Tick : Change()
    object Reset : Change()
    object Dummy : Change()
}

sealed class Action {
    object Dummy : Action()
}