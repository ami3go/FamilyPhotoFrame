package com.example.familyphotoframe.data.cache

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTransferCoordinatorTest {
    @Test fun selectedWaiterRequestsCooperativeYieldFromPreload() {
        runBlocking {
        val coordinator = RemoteTransferCoordinator()
        val preloadEntered = CountDownLatch(1)
        val releasePreload = CountDownLatch(1)
        var yieldSeen = false
        val preload = async(Dispatchers.Default) {
            coordinator.withPermit("source", RemoteTransferCoordinator.Priority.PRELOAD) { lease ->
                preloadEntered.countDown()
                while (!lease.shouldYield()) delay(5)
                yieldSeen = true
                releasePreload.await(1, TimeUnit.SECONDS)
            }
        }
        assertTrue(preloadEntered.await(1, TimeUnit.SECONDS))
        val selected = async(Dispatchers.Default) {
            coordinator.withPermit("source", RemoteTransferCoordinator.Priority.SELECTED_MEDIA) {
                true
            }
        }
        repeat(100) {
            if (yieldSeen) return@repeat
            delay(5)
        }
        assertTrue(yieldSeen)
        assertFalse(selected.isCompleted)
        releasePreload.countDown()
        preload.await()
        assertTrue(selected.await())
        }
    }

    @Test fun differentSourcesDoNotBlockEachOther() {
        runBlocking {
        val coordinator = RemoteTransferCoordinator()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val first = async(Dispatchers.Default) {
            coordinator.withPermit("one", RemoteTransferCoordinator.Priority.SELECTED_MEDIA) {
                firstEntered.countDown()
                releaseFirst.await(1, TimeUnit.SECONDS)
            }
        }
        assertTrue(firstEntered.await(1, TimeUnit.SECONDS))
        val second = coordinator.withPermit(
            "two",
            RemoteTransferCoordinator.Priority.CONTENT_HASH,
        ) { true }
        assertTrue(second)
        releaseFirst.countDown()
        first.await()
        }
    }
}
