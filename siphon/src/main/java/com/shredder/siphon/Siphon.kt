package com.shredder.siphon


import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan

data class Effect<State : Any, Action : Any>(
    val state: State,
    val actions: List<Action> = emptyList()
)

typealias Reducer<State, Change, Action> = (state: State, change: Change) -> Effect<State, Action>

class Siphon<State : Any, Change : Any, Action : Any>(
    initialState: State,
    private val reducer: Reducer<State, Change, Action>,
    private val actions: (action: Action) -> Flow<Change>,
    private val events: List<Flow<Change>> = emptyList()
) {

    private val changeRelay = BroadcastChannel<Change>(Channel.BUFFERED)
    private val actionRelay = BroadcastChannel<Action>(Channel.BUFFERED)
    private val changeFlow: Flow<Change> = changeRelay.asFlow()
    private val actionFlow: Flow<Change> =
        actionRelay.asFlow().flatMapMerge { actions(it) }

    private val changes = merge(actionFlow, changeFlow)

    val state: Flow<State> = changes
        .scan(initialState) { oldState, change ->
            val effect = reducer(oldState, change)
            effect.actions.forEach {
                if (!actionRelay.offer(it)) println("Action Buffer overload")
            }
            effect.state
        }

    fun change(change: Change) {
        if (!changeRelay.offer(change)) println("Change Buffer overload")
    }
}