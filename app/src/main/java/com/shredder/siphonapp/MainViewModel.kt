package com.shredder.siphonapp

import com.shredder.siphon.Effect
import com.shredder.siphon.Siphon
import kotlinx.coroutines.GlobalScope
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

    private val siphon = Siphon<State, Change, Action>(
        initialState = State(time = Duration.ZERO),
        reducer = { state, change ->
            when (change) {
                is Change.SetUsers -> Effect(state.copy(users = change.users,getUsersEnabled = true)) //, listOf(Action.Dummy))
                Change.Tick -> Effect(state.copy(time = state.time.plus(Duration.seconds(1))))
                Change.Reset -> Effect(State(time = Duration.ZERO))
                Change.OnClickGetUsers -> Effect(
                    state.copy(getUsersEnabled = false),
                    listOf(Action.GetUsers)
                )
            }
        },
        actions = { action ->
            when (action) {
                Action.GetUsers -> flow { emit(service.getUsers()) }
                    .map { Change.SetUsers(it) }
            }
        },
        events = listOf(
            flow {
                while (currentCoroutineContext().isActive) {
                    delay(1000)
                    emit(Change.Tick)
                }
            },
        ),
        coroutineScope = GlobalScope
    )

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