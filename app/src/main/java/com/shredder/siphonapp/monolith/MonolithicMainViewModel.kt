package com.shredder.siphonapp.monolith

import com.shredder.siphon.siphon
import com.shredder.siphonapp.BackendService
import com.shredder.siphonapp.DefaultBackendService
import com.shredder.siphonapp.MainViewModel
import com.shredder.siphonapp.State
import com.shredder.siphonapp.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
@FlowPreview
class MonolithicMainViewModel(
    private val scope: CoroutineScope,
    private val service: BackendService = DefaultBackendService(),
) : MainViewModel {

    private val siphon = siphon<State, Change, Action> {
        life {
            lifesycleScope = scope
            reduceOn = Dispatchers.IO
        }
        state {
            initial = State(time = Duration.ZERO)
            watchAll {
                println("State: $it")
            }
        }
        changes {
            reduce { change ->
                when (change) {
                    is Change.SetUsers -> this.copy(
                        users = change.users,
                        getUsersEnabled = true
                    ).only
                    Change.Tick -> this.copy(time = this.time.plus(Duration.seconds(1))).only
                    Change.Reset -> State(time = Duration.ZERO).only
                    Change.OnClickGetUsers ->
                        this.copy(getUsersEnabled = false) + Action.GetUsers
                }
            }
            watchAll {
                println("Change: $it")
            }
            intercept {
                println("Change Intercepted: $it")
                it
            }
        }

        actions {
//            performAll {
//                when (it) {
//                    Action.GetUsers ->
//                        flow { emit(service.getUsers()) }
//                            .map { Change.SetUsers(it) }
//                }
//            }
            perform<Action.GetUsers> {
                flow { emit(service.getUsers()) }
                    .map { Change.SetUsers(it) }
            }
            watchAll {
                println("Action: $it")
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

    override fun reset() {
        siphon.change(Change.Reset)
    }

    override fun getUsers() {
        siphon.change(Change.OnClickGetUsers)
    }

    override val users = siphon.state.map { it.users.map { it.name } }

    override val getUsersEnabled: Flow<Boolean> = siphon.state.map { it.getUsersEnabled }

    override val text: Flow<String> = siphon.state.map { it.time.inWholeSeconds.toString() }
}

sealed class Change {
    object Tick : Change()
    object Reset : Change()
    object OnClickGetUsers : Change()
    data class SetUsers(val users: List<User>) : Change()
}

sealed class Action {
    object GetUsers : Action()
}