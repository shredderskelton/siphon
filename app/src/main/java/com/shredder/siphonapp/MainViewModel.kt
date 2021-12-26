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

interface MainViewModel {
    val text: Flow<String>
    val getUsersEnabled: Flow<Boolean>
    val users: Flow<List<String>>
    fun reset()
    fun getUsers()
}

@ExperimentalTime
class MainViewModelImpl(private val service: BackendService = DefaultBackendService()) :
    MainViewModel {

    private val siphon = siphon<State, Change, Action> {
        state {
            initial = State(time = Duration.ZERO)
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

@ExperimentalTime
data class State(
    val time: Duration = Duration.ZERO,
    val getUsersEnabled: Boolean = true,
    val users: List<User> = emptyList()
)

sealed class Change {
    object Tick : Change()
    object Reset : Change()
    object OnClickGetUsers : Change()
    data class SetUsers(val users: List<User>) : Change()
}

sealed class Action {
    object GetUsers : Action()
}