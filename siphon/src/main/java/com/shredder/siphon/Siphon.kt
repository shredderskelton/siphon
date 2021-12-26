package com.shredder.siphon


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn

data class Effect<State : Any, Action : Any>(
    val state: State,
    val actions: List<Action> = emptyList()
)

typealias Reducer<State, Change, Action> = (state: State, change: Change) -> Effect<State, Action>

/** A function used for consuming events of given type. */
typealias Watcher<Type> = (value: Type) -> Unit

class Siphon<State : Any, Change : Any, Action : Any>(
    initialState: State,
    private val reducer: Reducer<State, Change, Action>,
    private val actions: (action: Action) -> Flow<Change>,
    private val events: List<Flow<Change>> = emptyList(),
    private val coroutineScope: CoroutineScope
) {

    private val changeRelay = MutableSharedFlow<Change>(0, 1, BufferOverflow.SUSPEND)
    private val actionRelay = MutableSharedFlow<Action>(0, 1, BufferOverflow.SUSPEND)
    private val changeFlow: Flow<Change> = changeRelay
    private val actionFlow: Flow<Change> = actionRelay.flatMapMerge { actions(it) }

    private val changes = merge(*events.plus(actionFlow).plus(changeFlow).toTypedArray())

    val state: Flow<State> = changes
        .onEach {
            println("Change: $it")
        }
        .scan(initialState) { oldState, change ->
            val effect = reducer(oldState, change)
            effect.actions.forEach {
                if (!actionRelay.tryEmit(it)) println("Action Buffer overload")
            }
            effect.state
        }
        .shareIn(coroutineScope, SharingStarted.WhileSubscribed(), 1)

    fun change(change: Change) {
        if (!changeRelay.tryEmit(change)) println("Change Buffer overload")
    }
}