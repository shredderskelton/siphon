package com.shredder.siphon

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

fun <State : Any, Change : Any, Action : Any> siphon(
    block: SiphonBuilder<State, Change, Action>.() -> Unit
): Siphon<State, Change, Action> =
    SiphonBuilder<State, Change, Action>()
        .also(block)
        .build()

@DslMarker
annotation class SiphonDsl

class SiphonBuilder<State : Any, Change : Any, Action : Any>
internal constructor() {

    private var initialState: State? = null
    private var observeOn: CoroutineDispatcher? = null
    private var reduceOn: CoroutineDispatcher? = null
    private var reducer: Reducer<State, Change, Action>? = null

    private val eventSources = mutableListOf<EventSource<Change>>()

    //    private val coldEventSources = lazy { mutableListOf<EventSource<Change>>() }
    private var action: (suspend (Action) -> Change)? = null
//    private val stateInterceptors = mutableListOf<Interceptor<State>>()
//    private val changeInterceptors = mutableListOf<Interceptor<Change>>()
//    private val actionInterceptors = mutableListOf<Interceptor<Action>>()

    /** A section for [State] and [Change] related declarations. */
    fun state(block: StateBuilder<State>.() -> Unit) {
        StateBuilder<State>().also {
            block(it)
            initialState = it.initial
//                observeOn = it.observeOn
        }
    }
//
    /** A section for [Change] related declarations. */
    fun changes(block: ChangesBuilder<State, Change, Action>.() -> Unit) {
        ChangesBuilder<State, Change, Action>()
            .also {
                block(it)
                reducer = it.reducer
            }
    }
//
    /** A section for [Action] related declarations. */
    fun actions(block: ActionsBuilder<Change, Action>.() -> Unit) {
        ActionsBuilder<Change, Action>()
            .also {
                block(it)
                action = it.action
            }
    }

    /** A section for *Event* related declarations. */
    fun events(block: EventsBuilder<Change>.() -> Unit) {
        EventsBuilder(eventSources).also(block)
    }

    internal fun build(): Siphon<State, Change, Action> = Siphon(
        initialState = checkNotNull(initialState) { "state { initial } must be declared" },
//        observeOn = observeOn,
//        reduceOn = reduceOn,
        reducer = checkNotNull(reducer) { "changes { reduce } must be declared" },
        events = eventSources.map { it.invoke() },
//        coldEventSources = coldEventSources,
        actions = checkNotNull(action) { "actions { actions } must be declared" },
//        stateInterceptors = stateInterceptors,
//        changeInterceptors = changeInterceptors,
//        actionInterceptors = actionInterceptors
    )

    @SiphonDsl
    class ChangesBuilder<State : Any, Change : Any, Action : Any>
    internal constructor(
        //private val changeInterceptors: MutableList<Interceptor<Change>>
    ) {
        internal var reducer: Reducer<State, Change, Action>? = null

        /**
         * Mandatory reduce function which receives the current [State] and a [Change]
         * and must return [Effect] with a new [State] and an optional [Action].
         *
         * New `State` and `Action` can be joined together using overloaded [State.plus()]
         * operator. For returning `State` without action call *.only* on the state.
         *
         * Example:
         * ```
         *  changes {
         *      reduce { change ->
         *          when (change) {
         *              is Change.Load -> copy(value = "loading") + Action.Load
         *              is Change.Load.Success -> copy(value = change.payload).only
         *              is Change.Load.Failure -> copy(value = "failed").only
         *          }
         *      }
         *  }
         * ```
         */
        fun reduce(reducer: Reducer<State, Change, Action>) {
            this.reducer = reducer
        }

        /** Turns [State] into an [Effect] without [Action]. */
        val State.only: Effect<State, Action> get() = Effect(this, emptyList())

        /** Combines [State] and [Action] into [Effect]. */
        operator fun State.plus(action: Action?) = Effect(this, listOfNotNull(action))

        /** Throws [IllegalStateException] with current [State] and given [Change] in its message. */
        fun State.unexpected(change: Change): Nothing = error("Unexpected $change in $this")

        /**
         * Executes given block if the knot is in the given state or
         * ignores the change in any other states.
         *
         * ```
         * reduce<Change> {
         *    whenState<State.Content> {
         *       ...
         *    }
         * }
         * ```
         * is a better readable alternative to
         * ```
         * reduce<Change> {
         *    when(this) {
         *       is State.Content -> ...
         *       else -> only
         *    }
         * }
         * ```
         */
        inline fun <reified WhenState : State> State.whenState(
            block: WhenState.() -> Effect<State, Action>
        ): Effect<State, Action> =
            if (this is WhenState) block()
            else Effect(this, emptyList())

        /**
         * Executes given block if the knot is in the given state or
         * throws [IllegalStateException] for the change in any other state.
         *
         * ```
         * reduce<Change> { change ->
         *    requireState<State.Content>(change) {
         *       ...
         *    }
         * }
         * ```
         * is a better readable alternative to
         * ```
         * reduce<Change> { change ->
         *    when(this) {
         *       is State.Content -> ...
         *       else -> unexpected(change)
         *    }
         * }
         * ```
         */
        inline fun <reified WhenState : State> State.requireState(
            change: Change, block: WhenState.() -> Effect<State, Action>
        ): Effect<State, Action> =
            if (this is WhenState) block()
            else unexpected(change)
    }
}

@SiphonDsl
class StateBuilder<State : Any> {
    var initial: State? = null
}

@SiphonDsl
class ActionsBuilder<Change : Any, Action : Any> {
    var action: (suspend (Action) -> Change)? = null

    // TODO inline the type
    fun perform(block: suspend (Action) -> Change) {
        action = block
    }
}

typealias EventSource<Change> = () -> Flow<Change>

@SiphonDsl
class EventsBuilder<Change : Any>
internal constructor(
    private val eventSources: MutableList<EventSource<Change>>
) {
    fun source(source: EventSource<Change>) {
        eventSources += source
    }
}

