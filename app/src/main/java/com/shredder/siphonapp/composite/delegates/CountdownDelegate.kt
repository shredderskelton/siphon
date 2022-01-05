package com.shredder.siphonapp.composite.delegates

import com.shredder.siphon.CompositeSiphon
import com.shredder.siphonapp.composite.Event
import com.shredder.siphonapp.composite.State
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@ExperimentalTime
class CountdownDelegate : Delegate {

    private sealed class Change {
        object Tick : Change()
        object Reset : Change()
    }

    override fun CompositeSiphon<State>.register() {
        registerDelegate<Change, Nothing> {
            changes {
                reduce<Change.Reset> {
                    this.copy(time = Duration.ZERO).only
                }
                reduce<Change.Tick> {
                    this.copy(time = this.time.plus(Duration.seconds(1))).only
                }
            }
            events {
                source {
                    flow {
                        while (currentCoroutineContext().isActive) {
                            delay(1000)
                            emit(Change.Tick)
                        }
                    }
                }
            }
        }
    }

    override suspend fun CompositeSiphon<State>.onEvent(event: Event): Boolean =
        when (event) {
            Event.OnClickGetUsers -> false
            Event.Reset -> {
                change.emit(Change.Reset)
                true
            }
        }
}
