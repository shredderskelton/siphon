package com.shredder.siphon

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlin.reflect.KClass

/** Creates a [CompositeSiphon]. */
@ExperimentalCoroutinesApi
@FlowPreview
fun <State : Any> compositeSiphon(
    block: CompositeSiphonBuilder<State>.() -> Unit
): CompositeSiphon<State> =
    CompositeSiphonBuilder<State>()
        .also(block)
        .build(
            actionSubject = MutableSharedFlow<Any>(0, 100, BufferOverflow.SUSPEND)
        )

/** A configuration builder for a [CompositeSiphon]. */
@ExperimentalCoroutinesApi
@FlowPreview
@SiphonDsl
class CompositeSiphonBuilder<State : Any>
internal constructor() {

    private var initialState: State? = null

    private var lifecycleScope: CoroutineScope? = null
    private var reduceOn: CoroutineDispatcher? = null
    private val stateInterceptors = mutableListOf<Interceptor<State>>()
    private val changeInterceptors = mutableListOf<Interceptor<Any>>()
    private val actionInterceptors = mutableListOf<Interceptor<Any>>()

    fun life(block: SiphonBuilder.LifeBuilder.() -> Unit) {
        SiphonBuilder.LifeBuilder().also {
            block(it)
            lifecycleScope = it.lifesycleScope
        }
    }

    /** A section for [State] related declarations. */
    fun state(block: SiphonBuilder.StateBuilder<State>.() -> Unit) {
        SiphonBuilder.StateBuilder(stateInterceptors)
            .also {
                block(it)
                initialState = it.initial
//                observeOn = it.observeOn
            }
    }

    /** A section for `Change` related declarations. */
    fun changes(block: ChangesBuilder.() -> Unit) {
        ChangesBuilder(changeInterceptors)
            .also {
                block(it)
                reduceOn = it.reduceOn
            }
    }

    /** A section for `Action` related declarations. */
    fun actions(block: ActionsBuilder.() -> Unit) {
        ActionsBuilder(actionInterceptors).also(block)
    }

    internal fun build(
        actionSubject: MutableSharedFlow<Any>
    ) = DefaultCompositeSiphon(
        initialState = checkNotNull(initialState) { "state { initial } must be set" },
//        observeOn = observeOn,
        reduceOn = reduceOn,
        stateInterceptors = stateInterceptors,
        changeInterceptors = changeInterceptors,
        actionInterceptors = actionInterceptors,
        actionSubject = actionSubject,
        lifecycleScope = checkNotNull(lifecycleScope) { "Lifecycle Scope must be set" },

    )

    @SiphonDsl
    class LifeBuilder {
        var lifesycleScope: CoroutineScope? = null
    }

    /** A configuration builder for `Changes`. */
    @SiphonDsl
    class ChangesBuilder
    internal constructor(
        private val changeInterceptors: MutableList<Interceptor<Any>>
    ) {
        /** An optional [CoroutineDispatcher] used for reduce function. */
        var reduceOn: CoroutineDispatcher? = null

        /** An optional [CoroutineDispatcher] used for watching *Changes*. */
//        var watchOn: CoroutineDispatcher? = null
//            set(value) {
//                field = value
//                value?.let { changeInterceptors += WatchOnInterceptor(it) }
//    }

        /** A function for watching `Change` emissions. */
        fun watchAll(watcher: Watcher<Any>) {
            changeInterceptors += WatchingInterceptor(watcher)
        }
    }

    /** A configuration builder for `Action` related declarations. */
    @SiphonDsl
    class ActionsBuilder
    internal constructor(
        private val actionInterceptors: MutableList<Interceptor<Any>>
    ) {
        /** An optional [CoroutineDispatcher] used for watching *Actions*. */
//        var watchOn: CoroutineDispatcher? = null
//            set(value) {`
//                field = value
//                value?.let { actionInterceptors += WatchOnInterceptor(it) }
//            }

        /** A function for watching `Action` emissions. */
        fun watchAll(watcher: Watcher<Any>) {
            actionInterceptors += WatchingInterceptor(watcher)
        }
    }
}

/** A configuration builder for a `Delegate`. */
@ExperimentalCoroutinesApi
@FlowPreview
@SiphonDsl
class DelegateBuilder<State : Any, Change : Any, Action : Any>
internal constructor(
    private val reducers: MutableMap<KClass<out Change>, Reducer<State, Change, Action>>,
    private val eventSources: MutableList<EventSource<Change>>,
    private val coldEventSources: Lazy<MutableList<EventSource<Change>>>,
    private val actionTransformers: MutableList<ActionTransformer<Action, Change>>,
    private val stateInterceptors: MutableList<Interceptor<State>>,
    private val changeInterceptors: MutableList<Interceptor<Change>>,
    private val actionInterceptors: MutableList<Interceptor<Action>>
) {

    /** A section for [State] related declarations. */
    fun state(block: StateBuilder<State>.() -> Unit) {
        StateBuilder(stateInterceptors).also(block)
    }

    /** A section for [Change] related declarations. */
    fun changes(block: ChangesBuilder<State, Change, Action>.() -> Unit) {
        ChangesBuilder(reducers, changeInterceptors).also(block)
    }

    /** A section for [Action] related declarations. */
    fun actions(block: ActionsBuilder<Change, Action>.() -> Unit) {
        ActionsBuilder(actionTransformers, actionInterceptors).also(block)
    }

    /** A section for `Event` related declarations. */
    fun events(block: EventsBuilder<Change>.() -> Unit) {
        EventsBuilder(eventSources).also(block)
    }

    /** A configuration builder for [State] related declarations. */
    @SiphonDsl
    class StateBuilder<State : Any>
    internal constructor(
        private val stateInterceptors: MutableList<Interceptor<State>>
    ) {
        /** An optional [CoroutineDispatcher] used for watching *State* updates. */
//        var watchOn: CoroutineDispatcher? = null
//            set(value) {
//                field = value
//                value?.let { stateInterceptors += WatchOnInterceptor(it) }
//            }

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

    /** A configuration builder for [Change] related declarations. */
    @SiphonDsl
    class ChangesBuilder<State : Any, Change : Any, Action : Any>
    internal constructor(
        private val reducers: MutableMap<KClass<out Change>, Reducer<State, Change, Action>>,
        @PublishedApi internal val changeInterceptors: MutableList<Interceptor<Change>>
    ) {
        /** An optional [CoroutineDispatcher] used for watching *Changes*. */
//        var watchOn: CoroutineDispatcher? = null
//            set(value) {
//                field = value
//                value?.let { changeInterceptors += WatchOnInterceptor(it) }
//            }

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
        fun reduce(changeType: KClass<out Change>, reduce: Reducer<State, Change, Action>) {
            reducers[changeType] = reduce
        }

        inline fun <reified C : Change> reduce(noinline reduce: Reducer<State, C, Action>) {
            @Suppress("UNCHECKED_CAST")
            reduce(C::class, reduce as Reducer<State, Change, Action>)
        }

        /** A function for intercepting [Change] emissions. */
        inline fun <reified C : Change> intercept(noinline interceptor: Interceptor<Change>) {
            changeInterceptors += { change ->
                interceptor(flowOf(change).filterIsInstance<C>())
            }
        }

        /** A function for watching emissions of all `Changes`. */
        inline fun <reified T : Change> watch(noinline watcher: Watcher<T>) {
            watchAll { change ->
                if (change is T) watcher(change)
            }
        }

        /** A function for watching [Change] emissions. */
        fun watchAll(watcher: Watcher<Change>) {
            changeInterceptors += WatchingInterceptor(watcher)
        }

        /** Turns [State] into an [Effect] without [Action]. */
        val State.only: Effect<State, Action> get() = Effect.WithAction(this)

        /** Combines [State] and [Action] into [Effect]. */
        operator fun State.plus(action: Action?) = Effect.WithAction(this, action)

        /** Throws [IllegalStateException] with current [State] and given [Change] in its message. */
        fun State.unexpected(change: Change): Nothing = error("Unexpected $change in $this")

        /**
         * Executes given block if the siphon is in the given state or
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
         * Executes given block if the siphon is in the given state or
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
            change: Change,
            block: WhenState.() -> Effect<State, Action>
        ): Effect<State, Action> =
            if (this is WhenState) block()
            else unexpected(change)

        /**
         * Dispatches partial state to each partial reducer in the collection.
         *
         * @param state is the initial partial state.
         * @param block declares the actual call to the partial reducer.
         * @return the effect with the final state and the list of actions to perform.
         *
         * ```
         * reduce<Change.Ignite> {
         *    partialReducers.dispatch(this) { reducer, partialState ->
         *       reducer.onStateChanged(partialState)
         *    }
         * }
         * See [Partial] for more details.
         */
        inline fun <State : Any, Action : Any, Reducer : Partial<State, Action>> Collection<Reducer>.dispatch(
            state: State,
            block: (reducer: Reducer, partialState: State) -> Effect<State, Action>
        ): Effect<State, Action> {
            val actions = mutableListOf<Action>()
            val newState = fold(state) { partialState, reducer ->
                when (val effect = block(reducer, partialState)) {
                    is Effect.WithAction -> {
                        effect.action?.let { actions += it }
                        effect.state
                    }
                    is Effect.WithActions -> {
                        actions += effect.actions
                        effect.state
                    }
                }
            }
            return if (actions.isEmpty()) Effect.WithAction(newState)
            else Effect.WithActions(newState, actions)
        }

        /**
         * Dispatches partial state to each partial reducer in the collection.
         *
         * @param state is the initial partial state.
         * @param block declares the actual call to the partial reducer.
         * @return the final state.
         *
         * ```
         * reduce<Change.Ignite> {
         *    partialReducers.dispatchStateOnly(this) { reducer, partialState ->
         *       reducer.onStateChanged(partialState)
         *    }.only
         * }
         * ```
         * See `PartialStateOnlyTest` for more details.
         */
        inline fun <State : Any, Reducer : Any> Collection<Reducer>.dispatchStateOnly(
            state: State,
            block: (reducer: Reducer, partialState: State) -> State
        ): State =
            fold(state) { partialState, reducer ->
                block(reducer, partialState)
            }
    }
}

/**
 * Marker interface for partial reducers. Partial reducer is used when multiple delegates
 * need to participate in handling of a single change.
 *
 * For example, you have a delegate responsible for handling a large data structure coming
 * from a backend. Each delegate should be able to use that structure for updating the part
 * of the shared state, related to each delegate's concern only. Partial delegation can be
 * implemented as follows:
 * - Create a delegate responsible for handling the structure in the first place.
 * - Declare a partial reducer interface to that delegate.
 * - Let other delegates involved in the handling implement the partial reducer interface.
 * - Collect all partial reducers in the main delegate and dispatch the state and the
 * structure though out all collected partial reducers.
 *
 * See `PartialStateWithActionsTest` to see the partial reducers in action.
 */
interface Partial<State : Any, Action : Any> {
    val State.only: Effect<State, Action>
        get() = Effect.WithAction(this)

    operator fun State.plus(action: Action?): Effect<State, Action> =
        Effect.WithAction(this, action)
}
