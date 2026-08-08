package com.example.familyphotoframe.domain.randomize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FolderCycleQueueTest {

    @Test fun firstCycleVisitsAll106FoldersExactlyOnce() {
        val queue = FolderCycleQueue()
        val folders = (1..106).map { "folder-$it" }
        queue.sync(folders, Random(52))

        val cycle = (1..106).mapNotNull { queue.next(Random(it)) }

        assertEquals(106, cycle.size)
        assertEquals(folders.toSet(), cycle.toSet())
        assertEquals(106, cycle.distinct().size)
    }

    @Test fun cycleSeamDoesNotRepeatFolder() {
        val queue = FolderCycleQueue()
        queue.sync(listOf("a", "b", "c", "d"), Random(2))
        val first = (1..4).mapNotNull { queue.next(Random(8)) }
        val second = (1..4).mapNotNull { queue.next(Random(9)) }

        assertNotEquals(first.last(), second.first())
        assertEquals(setOf("a", "b", "c", "d"), second.toSet())
    }

    @Test fun rescanPreservesCurrentFolderCycle() {
        val queue = FolderCycleQueue()
        queue.sync(listOf("a", "b", "c"), Random(1))
        val shown = queue.next(Random(1))!!

        queue.sync(listOf("a", "b", "c", "d"), Random(3))
        val rest = (1..3).mapNotNull { queue.next(Random(it)) }

        assertTrue(shown !in rest)
        assertEquals((setOf("a", "b", "c", "d") - shown), rest.toSet())
    }
}
