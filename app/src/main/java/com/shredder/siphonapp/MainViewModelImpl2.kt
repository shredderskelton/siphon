package com.shredder.siphonapp

import com.shredder.siphon.siphon
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@ExperimentalTime
class MainViewModelImpl2 : MainViewModel {

    private val siphon = siphon<State, Change, Action> {
        state {
            initial = State(Duration.ZERO)
        }
        changes {
            reduce { state, change ->
                when (change) {
                    Change.Dummy -> state.only
                    Change.Tick -> {
                        println("Tick")
                        state.copy(time = state.time.plus(1.seconds)).only
                    }
                    Change.Reset -> State(time = Duration.ZERO).only
                }
            }
        }
        actions {
            perform {
                when (it) {
                    Action.Dummy -> Change.Dummy
                }
            }
        }
        events {
            source {
                flow {
                    while (currentCoroutineContext().isActive) {
                        delay(1000)
                        println("Tick happening")
                        emit(Change.Tick)
                    }
                }
            }
            source {
                flow {
                    while (currentCoroutineContext().isActive) {
                        delay(11000)
                        println("Unusual Tick happening")
                        emit(Change.Tick)
                    }
                }
            }
        }
    }

    override fun reset() {
        siphon.change(Change.Reset)
    }

    override val text: Flow<String> = siphon.state.map { it.time.toIsoString() }
}