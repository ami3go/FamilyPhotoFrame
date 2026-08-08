package com.example.familyphotoframe.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Indirection over coroutine dispatchers so that no production code hard-codes
 * [Dispatchers.IO] / [Dispatchers.Default]. Contract Rule 2 forbids disk/network
 * I/O on the main thread; every source, cache, Room and EXIF call must run on
 * [io] or [default]. Tests substitute a single test dispatcher.
 */
interface AppDispatchers {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

object DefaultAppDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val io: CoroutineDispatcher get() = Dispatchers.IO
}
