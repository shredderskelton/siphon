package com.shredder.siphonapp.composite

import com.shredder.siphon.compositeSiphon
import com.shredder.siphonapp.composite.delegates.Delegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

interface CompositeMainSiphon {
    val state: Flow<State>
    fun onEvent(event: Event)
}

class DefaultCompositeMainSiphon(
    private val delegates: List<Delegate>,
    private val scope: CoroutineScope
) : CompositeMainSiphon {

    private val siphon = compositeSiphon<State> {
        life { lifesycleScope = scope }
        state { initial = State() }
    }
    override val state = siphon.state

    override fun onEvent(event: Event) {
        scope.launch {
            delegates.any {
                with(it) {
                    siphon.onEvent(event)
                }
            }
        }
    }

    init {
        delegates.forEach { delegate ->
            with(delegate) { siphon.register() }
        }
        siphon.compose()
    }
}

