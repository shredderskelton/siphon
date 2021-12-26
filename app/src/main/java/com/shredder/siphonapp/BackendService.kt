package com.shredder.siphonapp

import kotlinx.coroutines.delay
import kotlin.random.Random

data class User(val name:String)

interface BackendService {
    suspend fun getUsers():List<User>
}

private val users = listOf<User>(
    User("Nick"),
    User("Larry"),
    User("Jack"),
    User("Patty"),
    User("Greg"),
    User("Andrew"),
    User("Bob"),
    User("Fred"),
)

class DefaultBackendService():BackendService{
    override suspend fun getUsers(): List<User> {
        println("Getting users...")
        delay(Random.nextLong(1000, 2000))
        val users = listOf(users.random(), users.random())
        println("Getting users... done")
        return users
    }
}