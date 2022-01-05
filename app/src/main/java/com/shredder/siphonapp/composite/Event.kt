package com.shredder.siphonapp.composite

sealed class Event{
    object Reset : Event()
    object OnClickGetUsers : Event()
}