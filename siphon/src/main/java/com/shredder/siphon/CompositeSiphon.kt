package com.shredder.siphon

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

/**
 * If your [Siphon] becomes big and you want to improve its readability and maintainability,
 * you may consider to decompose it. You start decomposition by grouping related
 * concerns into, in a certain sense, indecomposable pieces called `Delegate`.
 *
 * [Flowchart diagram](https://github.com/beworker/knot/raw/master/docs/diagrams/flowchart-composite-knot.png)
 *
 * Each `Delegate` is isolated from the other `Delegates`. It defines its own set of
 * `Changes`, `Actions` and `Reducers`. It's only the `State`, that is shared between
 * the `Delegates`. In that respect each `Delegate` can be seen as a separate [Siphon] instance.
 *
 * Once all `Delegates` are registered at a `CompositeKnot`, the knot can be finally
 * composed using [compose] function and start operating.
 *
 * See [Composite ViewModel](https://www.halfbit.de/posts/composite-viewmodel/) for more details.
 */
@ExperimentalCoroutinesApi
@FlowPreview
interface CompositeSiphon<State : Any> : Siphon<State, Any> {

    /** Registers a new `Delegate` in this composite knot. */
    fun <Change : Any, Action : Any> registerDelegate(
        block: DelegateBuilder<State, Change, Action>.() -> Unit
    )

    /** Finishes composition of `Delegates` and moves this knot into the operational mode. */
    fun compose()
}

@ExperimentalCoroutinesApi
@FlowPreview
internal class DefaultCompositeSiphon<State : Any>(
    initialState: State,
    lifecycleScope: CoroutineScope,
    reduceOn: CoroutineDispatcher?,
    stateInterceptors: MutableList<Interceptor<State>>,
    changeInterceptors: MutableList<Interceptor<Any>>,
    actionInterceptors: MutableList<Interceptor<Any>>,
    actionSubject: MutableSharedFlow<Any>
) : CompositeSiphon<State> {

    private val coldEventSources = lazy { mutableListOf<EventSource<Any>>() }
    private val composition = AtomicReference(
        Composition(
            initialState,
            lifecycleScope,
            reduceOn,
            stateInterceptors,
            changeInterceptors,
            actionInterceptors,
            actionSubject
        )
    )

    private val stateSubject = MutableStateFlow(initialState)
    private val changeSubject = MutableSharedFlow<Any>(0, 100, BufferOverflow.SUSPEND)

    @Suppress("UNCHECKED_CAST")
    override fun <Change : Any, Action : Any> registerDelegate(
        block: DelegateBuilder<State, Change, Action>.() -> Unit
    ) {
        composition.get()?.let {
            DelegateBuilder(
                it.reducers,
                it.eventSources,
                coldEventSources,
                it.actionTransformers,
                it.stateInterceptors,
                it.changeInterceptors,
                it.actionInterceptors
            ).also(block as DelegateBuilder<State, Any, Any>.() -> Unit)
        } ?: error("Delegates cannot be registered after compose() was called")
    }

    override val state: Flow<State> = stateSubject

    override val change: MutableSharedFlow<Any> = changeSubject.apply {
        onSubscription {
            check(composition.get() == null) { "compose() must be called before emitting any change." }
        }
    }

//    @Synchronized
//    private fun maybeSubscribeColdEvents() {
//        if (coldEventSources.isInitialized() &&
//            coldEventsDisposable == null &&
//            subscriberCount.get() > 0
//        ) {
//            val coldEventsObservable =
//                this.coldEventsObservable
//                    ?: coldEventSources
//                        .mergeIntoObservable()
//                        .also { this.coldEventsObservable = it }
//
//            coldEventsDisposable =
//                coldEventsObservable
//                    .subscribe(
//                        changeSubject::onNext,
//                        changeSubject::onError
//                    )
//        }
//    }
//
//    @Synchronized
//    private fun maybeUnsubscribeColdEvents() {
//        coldEventsDisposable?.let {
//            it.dispose()
//            coldEventsDisposable = null
//        }
//    }

    override fun compose() {
        composition.getAndSet(null)?.let { composition ->
            composition.lifecycleScope.launch {
                composition.changeStream
                    .flattenMerge()
                    .intercept(composition.changeInterceptors)
                    .scan(composition.initialState) { state, change ->
                        val reducer = composition.reducers[change::class]
                        if (reducer == null) {
                            composition.reducers.forEach { (t, u) ->
                                println("key:$t - value: $u")
                            }
                            error("Cannot find reducer for $change")
                        }
                        reducer(state, change).emitActions(composition.actionSubject)
                    }
                    .distinctUntilChanged { prev, curr -> prev === curr }
//                    .let { stream -> composition.observeOn?.let { stream.observeOn(it) } ?: stream }
                    .intercept(composition.stateInterceptors)
                    .collectIndexed { _, value ->
                        stateSubject.value = value
                    }
            }
        }
    }

    private val Composition<State>.changeStream: Flow<Flow<Any>>
        get() {
            val changeStream: MutableList<Flow<Any>> = mutableListOf()
            changeStream.add(changeSubject)
            changeStream.add(actionSubject
                .intercept(actionInterceptors)
                .flatMapMerge { action ->
                    actionTransformers.asFlow()
                        .flatMapMerge { transformer -> transformer(action) }
                }
            )
            changeStream.addAll(eventSources.map { source -> source() })
            return changeStream.asFlow()
        }

    private class Composition<State : Any>(
        val initialState: State,
        val lifecycleScope: CoroutineScope,
        val reduceOn: CoroutineDispatcher?,
        val stateInterceptors: MutableList<Interceptor<State>>,
        val changeInterceptors: MutableList<Interceptor<Any>>,
        val actionInterceptors: MutableList<Interceptor<Any>>,
        val actionSubject: MutableSharedFlow<Any>
    ) {
        val reducers = mutableMapOf<KClass<out Any>, Reducer<State, Any, Any>>()
        val actionTransformers = mutableListOf<ActionTransformer<Any, Any>>()
        val eventSources = mutableListOf<EventSource<Any>>()
    }
}
