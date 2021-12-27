package com.shredder.siphon

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

sealed class Effect<State : Any, Action : Any> {
    /** Adds another action to [Effect]. */
    abstract operator fun plus(action: Action?): Effect<State, Action>

    data class WithAction<State : Any, Action : Any>(
        val state: State,
        val action: Action? = null
    ) : Effect<State, Action>() {
        override fun plus(action: Action?): Effect<State, Action> =
            when {
                action == null -> this
                this.action == null -> WithAction(state, action)
                else -> WithActions(state, listOf(this.action, action))
            }
    }

    data class WithActions<State : Any, Action : Any>(
        val state: State,
        val actions: List<Action>
    ) : Effect<State, Action>() {
        override fun plus(action: Action?): Effect<State, Action> =
            if (action == null) this
            else WithActions(state, actions + action)
    }
}

/** A function accepting the `State` and a `Change` and returning a new `State`. */
typealias Reducer<State, Change, Action> = State.(change: Change) -> Effect<State, Action>

/** A function returning an [Flow] `Change`. */
typealias EventSource<Change> = () -> Flow<Change>

/** A function used for performing given `Action` and emitting resulting `Change` or *Changes*. */
typealias ActionTransformer<Action, Change> = (action: Action) -> Flow<Change>

/** A function used for performing given `Action` and emitting resulting `Change` or *Changes*. */
typealias ActionTransformerWithReceiver<Action, Change> = Action.() -> Flow<Change>

/** A function used for consuming events of given type. */
typealias Watcher<Type> = (value: Type) -> Unit

class Siphon<State : Any, Change : Any, Action : Any>(
    initialState: State,
    private val reducer: Reducer<State, Change, Action>,
    private val actionTransformers: List<ActionTransformer<Action, Change>>,
    actionInterceptors: List<Interceptor<Action>>,
    changeInterceptors: List<Interceptor<Change>>,
    stateInterceptors: List<Interceptor<State>>,
    events: List<EventSource<Change>> = emptyList(),
    coroutineScope: CoroutineScope,
    private val reduceOn:CoroutineDispatcher,
) {

    private val changeRelay = MutableSharedFlow<Change>(0, 100, BufferOverflow.SUSPEND)
    private val actionRelay = MutableSharedFlow<Action>(0, 100, BufferOverflow.SUSPEND)
    private val changeFlow: Flow<Change> = changeRelay.intercept(changeInterceptors)
    private val actionFlow: Flow<Change> = actionRelay.intercept(actionInterceptors)
        .flatMapMerge { action ->
            actionTransformers.asFlow()
                .flatMapMerge { transformer -> transformer(action) }
        }

    private val eventFlow = events.map { it() }

    private val changes = merge(*eventFlow.plus(actionFlow).plus(changeFlow).toTypedArray())

    val state: Flow<State> = changes
        .scan(initialState) { oldState, change ->
            withContext(reduceOn) { reduce(oldState, change) }
        }
        .intercept(stateInterceptors)
        .shareIn(coroutineScope, SharingStarted.WhileSubscribed(), 1)

    private fun reduce(oldState: State, change: Change) =
        when (val effect = reducer(oldState, change)) {
            is Effect.WithActions -> {
                effect.actions.forEach {
                    if (!actionRelay.tryEmit(it)) println("Siphon: Action Buffer overload")
                }
                effect.state
            }
            is Effect.WithAction -> {
                if (effect.action != null)
                    if (!actionRelay.tryEmit(effect.action)) println("Siphon: Action Buffer overload")
                effect.state
            }
        }

    fun change(change: Change) {
        if (!changeRelay.tryEmit(change)) println("Siphon: Change Buffer overload")
    }
}

internal fun <T> Flow<T>.intercept(interceptors: List<Interceptor<T>>): Flow<T> =
    interceptors.fold(this) { state, intercept -> intercept(state) }
