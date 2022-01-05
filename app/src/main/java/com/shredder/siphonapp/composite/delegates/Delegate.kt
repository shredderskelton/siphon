package com.shredder.siphonapp.composite.delegates

import com.shredder.siphon.CompositeSiphon
import com.shredder.siphonapp.composite.Event
import com.shredder.siphonapp.composite.State

interface Delegate {
    fun CompositeSiphon<State>.register()
    suspend fun CompositeSiphon<State>.onEvent(event: Event): Boolean
}