package com.shredder.siphonapp.composite

import com.shredder.siphonapp.MainViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CompositeMainViewModel(private val siphon: CompositeMainSiphon) : MainViewModel {

    override fun getUsers() {
        siphon.onEvent(Event.OnClickGetUsers)
    }

    override val users = siphon.state.map { it.users.map { it.name } }

    override val getUsersEnabled: Flow<Boolean> = siphon.state.map { it.getUsersEnabled }

    override fun reset() {
        siphon.onEvent(Event.Reset)
    }

    override val text: Flow<String> = siphon.state.map { it.time.inWholeSeconds.toString() }

}