package com.example.familyphotoframe.domain.randomize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FolderBalancedPlaybackQueueTest {

    private val entries = listOf(
        FolderBalancedPlaybackQueue.Entry(1, "a"),
        FolderBalancedPlaybackQueue.Entry(2, "a"),
        FolderBalancedPlaybackQueue.Entry(3, "a"),
        FolderBalancedPlaybackQueue.Entry(11, "b"),
        FolderBalancedPlaybackQueue.Entry(12, "b"),
        FolderBalancedPlaybackQueue.Entry(13, "b"),
        FolderBalancedPlaybackQueue.Entry(21, "c"),
        FolderBalancedPlaybackQueue.Entry(22, "c"),
        FolderBalancedPlaybackQueue.Entry(23, "c"),
    )
    private val folderById = entries.associate { it.id to it.folder }

    @Test fun eachFolderAppearsOncePerOuterCycle() {
        val queue = FolderBalancedPlaybackQueue()
        queue.sync(entries, Random(7))

        val ids = (1..3).mapNotNull { queue.next(Random(it)) }
        val folders = ids.map { folderById.getValue(it) }

        assertEquals(setOf("a", "b", "c"), folders.toSet())
        assertEquals(3, folders.distinct().size)
    }

    @Test fun photosInsideEachFolderDoNotRepeatUntilTheirOwnCycleCompletes() {
        val queue = FolderBalancedPlaybackQueue()
        queue.sync(entries, Random(11))

        val firstNine = (1..9).mapNotNull { queue.next(Random(it + 20)) }
        for (folder in listOf("a", "b", "c")) {
            val ids = firstNine.filter { folderById.getValue(it) == folder }
            assertEquals(3, ids.size)
            assertEquals(3, ids.distinct().size)
        }
    }

    @Test fun folderDoesNotRepeatAcrossOuterCycleSeam() {
        val queue = FolderBalancedPlaybackQueue()
        queue.sync(entries, Random(4))
        val first = (1..3).mapNotNull { queue.next(Random(1)) }
        val next = queue.next(Random(2))!!

        assertNotEquals(folderById.getValue(first.last()), folderById.getValue(next))
    }

    @Test fun collageCompanionIsConsumedWithoutSpendingAnotherFolderTurn() {
        val queue = FolderBalancedPlaybackQueue()
        queue.sync(entries, Random(6))
        val anchor = queue.next(Random(6))!!
        val folder = folderById.getValue(anchor)
        val companion = entries.first { it.folder == folder && it.id != anchor }.id
        queue.consume(listOf(companion))

        val laterSameFolder = generateSequence { queue.next(Random(9)) }
            .first { folderById.getValue(it) == folder }

        assertTrue(laterSameFolder != anchor)
        assertTrue(laterSameFolder != companion)
    }
}
