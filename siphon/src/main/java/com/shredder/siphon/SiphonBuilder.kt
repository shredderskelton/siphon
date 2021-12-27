package com.shredder.siphon

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach

/** Creates a [Siphon] instance. */
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
    private var liveIn: CoroutineScope? = null
    private var reducer: Reducer<State, Change, Action>? = null

    private var reduceOn: CoroutineDispatcher = Dispatchers.Default

    private val eventSources = mutableListOf<EventSource<Change>>()
    private val actionTransformers = mutableListOf<ActionTransformer<Action, Change>>()
    private val stateInterceptors = mutableListOf<Interceptor<State>>()
    private val changeInterceptors = mutableListOf<Interceptor<Change>>()
    private val actionInterceptors = mutableListOf<Interceptor<Action>>()

    fun life(block: LifeBuilder.() -> Unit) {
        LifeBuilder().also {
            block(it)
            liveIn = it.lifesycleScope
            reduceOn = it.reduceOn
        }
    }

    /** A section for [State] and [Change] related declarations. */
    fun state(block: StateBuilder<State>.() -> Unit) {
        StateBuilder(stateInterceptors)
            .also {
                block(it)
                initialState = it.initial
            }
    }

    /** A section for [Change] related declarations. */
    fun changes(block: ChangesBuilder<State, Change, Action>.() -> Unit) {
        ChangesBuilder<State, Change, Action>(changeInterceptors)
            .also {
                block(it)
                reducer = it.reducer
            }
    }

    /** A section for [Action] related declarations. */
    fun actions(block: ActionsBuilder<Change, Action>.() -> Unit) {
        ActionsBuilder(actionTransformers, actionInterceptors).also(block)
    }

    /** A section for *Event* related declarations. */
    fun events(block: EventsBuilder<Change>.() -> Unit) {
        EventsBuilder(eventSources).also(block)
    }

    internal fun build(): Siphon<State, Change, Action> = Siphon(
        initialState = checkNotNull(initialState) { "state { initial } must be declared" },
        reduceOn = reduceOn,
        reducer = checkNotNull(reducer) { "changes { reduce } must be declared" },
        events = eventSources,
        actionTransformers = actionTransformers,
        actionInterceptors = actionInterceptors,
        stateInterceptors = stateInterceptors,
        changeInterceptors = changeInterceptors,
        coroutineScope = checkNotNull(liveIn) { "You must define a scope for the Siphon to live in" },
    )

    @SiphonDsl
    class ChangesBuilder<State : Any, Change : Any, Action : Any>
    internal constructor(
        private val changeInterceptors: MutableList<Interceptor<Change>>
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

        /** A function for intercepting [Change] emissions. */
        fun intercept(interceptor: Interceptor<Change>) {
            changeInterceptors += interceptor
        }

        /** A function for watching [Change] emissions. */
        fun watchAll(watcher: Watcher<Change>) {
            changeInterceptors += WatchingInterceptor(watcher)
        }

        /** A function for watching emissions of all `Changes`. */
        inline fun <reified T : Change> watch(noinline watcher: Watcher<T>) {
            watchAll { change ->
                flowOf(change).filterIsInstance<T>().onEach { watcher(it) }
            }
        }

        /** Turns [State] into an [Effect] without [Action]. */
        val State.only: Effect<State, Action> get() = Effect.WithAction(this, null)

        /** Combines [State] and [Action] into [Effect]. */
        operator fun State.plus(action: Action?) = Effect.WithAction(this, action)

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
            else Effect.WithAction(this, null)

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

    @SiphonDsl
    class LifeBuilder {
        var lifesycleScope: CoroutineScope? = null
        var reduceOn: CoroutineDispatcher = Dispatchers.Default
    }

    /** A configuration builder for [State] related declarations. */
    @SiphonDsl
    class StateBuilder<State : Any>
    internal constructor(
        private val stateInterceptors: MutableList<Interceptor<State>>
    ) {
        /** Mandatory initial [State] of the [Knot]. */
        var initial: State? = null

        /** A function for intercepting [State] mutations. */
        fun intercept(interceptor: Interceptor<State>) {
            stateInterceptors += interceptor
        }

        /** A function for watching mutations of any [State]. */
        fun watchAll(watcher: Watcher<State>) {
            stateInterceptors += WatchingInterceptor(watcher)
        }

        /** A function for watching mutations of all `States`. */
        inline fun <reified T : State> watch(noinline watcher: Watcher<T>) {
            watchAll { state ->
                if (state is T) watcher(state)
            }
        }
    }

    @SiphonDsl
    class ActionsBuilder<Change : Any, Action : Any>
    internal constructor(
        private val actionTransformers: MutableList<ActionTransformer<Action, Change>>,
        private val actionInterceptors: MutableList<Interceptor<Action>>
    ) {
        /** A function used for declaring an [ActionTransformer] function. */
        @PublishedApi
        internal fun performAll(transformer: ActionTransformer<Action, Change>) {
            actionTransformers += transformer
        }

        /**
         * A function used for declaring an [ActionTransformer] function for given [Action] type.
         *
         * Example:
         * ```
         *  actions {
         *      perform<Action.Load> {
         *          flatMapSingle {
         *              loadData()
         *                  .map<Change> { Change.Load.Success(it) }
         *                  .onErrorReturn { Change.Load.Failure(it) }
         *          }
         *      }
         *  }
         * ```
         */
        inline fun <reified A : Action> perform(noinline transformer: ActionTransformerWithReceiver<A, Change>) {
            performAll { action ->
                flowOf(action).filterIsInstance<A>().flatMapMerge { transformer(it) }
            }
        }

        fun intercept(interceptor: Interceptor<Action>) {
            actionInterceptors += interceptor
        }

        fun watchAll(watcher: Watcher<Action>) {
            actionInterceptors += WatchingInterceptor(watcher)
        }

        inline fun <reified T : Action> watch(noinline watcher: Watcher<T>) {
            watchAll { action ->
                if (action is T) watcher(action)
            }
        }
    }

    @SiphonDsl
    class EventsBuilder<Change : Any>
    internal constructor(
        private val eventSources: MutableList<EventSource<Change>>,
    ) {
        fun source(source: EventSource<Change>) {
            eventSources += source
        }
    }
}

/** A function used for intercepting events of given type. */
typealias Interceptor<Type> = (value: Flow<Type>) -> Flow<Type>

internal class WatchingInterceptor<T>(
    private val watcher: Watcher<T>
) : Interceptor<T> {
    override fun invoke(stream: Flow<T>): Flow<T> = stream.onEach { watcher(it) }
}