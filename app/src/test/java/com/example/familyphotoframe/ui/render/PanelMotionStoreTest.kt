package com.example.familyphotoframe.ui.render

import com.example.familyphotoframe.domain.engine.CollageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PanelMotionStoreTest {

    @Test fun twoPhotoCollageDoesNotAllocateMotionState() {
        val store = PanelMotionStore()
        var builds = 0

        val entry = store.entryForThreePanel(
            slideId = 1L,
            signature = "SUBTLE|15000",
            layout = CollageLayout.TWO_COLUMNS,
            panelCount = 2,
        ) {
            builds += 1
            emptyList()
        }

        assertNull(entry)
        assertEquals(0, builds)
        assertEquals(0, store.size)
    }

    @Test fun malformedThreeColumnCollageDoesNotAllocateMotionState() {
        val store = PanelMotionStore()

        val entry = store.entryForThreePanel(
            slideId = 2L,
            signature = "SUBTLE|15000",
            layout = CollageLayout.THREE_COLUMNS,
            panelCount = 2,
        ) { emptyList() }

        assertNull(entry)
        assertEquals(0, store.size)
    }

    @Test fun eligibleSlideReusesStateUntilItsSignatureChanges() {
        val store = PanelMotionStore()
        var builds = 0
        fun entry(signature: String): PanelMotionStore.Entry? = store.entryForThreePanel(
            slideId = 3L,
            signature = signature,
            layout = CollageLayout.THREE_COLUMNS,
            panelCount = 3,
        ) {
            builds += 1
            null
        }

        val first = entry("SUBTLE|15000")
        val reused = entry("SUBTLE|15000")
        val rebuilt = entry("REDUCED|15000")

        assertSame(first, reused)
        assertNotSame(first, rebuilt)
        assertEquals(2, builds)
        assertEquals(1, store.size)
    }

    @Test fun retainDropsEntriesForSlidesThatLeftTheComposition() {
        val store = PanelMotionStore()
        listOf(10L, 11L, 12L).forEach { slideId ->
            store.entryForThreePanel(
                slideId = slideId,
                signature = "SUBTLE|15000",
                layout = CollageLayout.THREE_COLUMNS,
                panelCount = 3,
            ) { null }
        }

        store.retain(setOf(11L))
        val retained = store.entryForThreePanel(
            slideId = 11L,
            signature = "SUBTLE|15000",
            layout = CollageLayout.THREE_COLUMNS,
            panelCount = 3,
        ) { error("retained entry should be reused") }

        assertEquals(1, store.size)
        assertSame(retained, store.entryForThreePanel(
            slideId = 11L,
            signature = "SUBTLE|15000",
            layout = CollageLayout.THREE_COLUMNS,
            panelCount = 3,
        ) { error("retained entry should be reused") })
    }
}
