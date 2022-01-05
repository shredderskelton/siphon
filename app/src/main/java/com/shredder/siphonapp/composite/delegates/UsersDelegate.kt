package com.shredder.siphonapp.composite.delegates

import com.shredder.siphon.CompositeSiphon
import com.shredder.siphonapp.BackendService
import com.shredder.siphonapp.DefaultBackendService
import com.shredder.siphonapp.User
import com.shredder.siphonapp.composite.Event
import com.shredder.siphonapp.composite.State
import kotlin.time.ExperimentalTime

@ExperimentalTime
class UsersDelegate(
    private val service: BackendService = DefaultBackendService(),
) : Delegate {

    private sealed class Change {
        object OnClickGetUsers : Change()
        data class SetUsers(val users: List<User>) : Change()
    }

    private sealed class Action {
        object GetUsers : Action()
    }

    override fun CompositeSiphon<State>.register() {
        registerDelegate<Change, Action> {
            changes {
                    reduce<Change.SetUsers> { change ->
                        this.copy(
                            users = change.users,
                            getUsersEnabled = true
                        ).only
                    }
                reduce<Change.OnClickGetUsers>{ change ->
                        this.copy(getUsersEnabled = false) + Action.GetUsers
                }
            }
        }
    }

    override suspend fun CompositeSiphon<State>.onEvent(event: Event): Boolean =
        when (event) {
            Event.OnClickGetUsers -> {
                val users = service.getUsers()
                change.emit(Change.SetUsers(users))
                true
            }
            Event.Reset -> false
        }
}
