package com.example.familyphotoframe.ui.slideshow

import com.example.familyphotoframe.data.source.OpenOptions
import com.example.familyphotoframe.data.source.PhotoItem
import com.example.familyphotoframe.data.source.PhotoSource
import com.example.familyphotoframe.data.source.ScanCursor
import com.example.familyphotoframe.data.source.ScanEvent
import com.example.familyphotoframe.data.source.ScanOptions
import com.example.familyphotoframe.data.source.SourceHealth
import com.example.familyphotoframe.data.source.SourceId
import com.example.familyphotoframe.data.source.SourceType
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RemoteSourceRegistryTest {
    private class FakeSource(name: String) : PhotoSource {
        override val id = SourceId(name)
        override val type = SourceType.SMB_SOURCE
        var closeCount = 0

        override suspend fun healthCheck(timeoutMs: Long) = SourceHealth.Ok

        override fun scan(previousCursor: ScanCursor?, options: ScanOptions): Flow<ScanEvent> = emptyFlow()

        override suspend fun openStream(item: PhotoItem, options: OpenOptions): InputStream =
            ByteArrayInputStream(byteArrayOf())

        override fun close() {
            closeCount++
        }
    }

    @Test fun concurrentBackfillBuildersKeepOneAndCloseTheLoser() {
        val registry = RemoteSourceRegistry()
        val first = FakeSource("first")
        val loser = FakeSource("loser")

        assertSame(first, registry.retainBackfill("smb", first))
        assertSame(first, registry.retainBackfill("smb", loser))
        assertEquals(0, first.closeCount)
        assertEquals(1, loser.closeCount)
    }

    @Test fun promotionAtomicallyReplacesAndClosesBackfillSource() {
        val registry = RemoteSourceRegistry()
        val backfill = FakeSource("backfill")
        val active = FakeSource("active")
        registry.retainBackfill("smb", backfill)

        registry.promote("smb", active)

        assertSame(active, registry.active("smb"))
        assertSame(active, registry.resolved("smb"))
        assertEquals(1, backfill.closeCount)
        assertEquals(0, active.closeCount)

        registry.releaseAll()
        assertEquals(1, active.closeCount)
    }

    @Test fun newestReplacementGenerationKeepsLateBackfillsBlocked() {
        val registry = RemoteSourceRegistry()
        val firstGeneration = registry.beginReplacement()
        val newestGeneration = registry.beginReplacement()
        registry.finishReplacement(firstGeneration)
        val rejected = FakeSource("rejected")

        assertNull(registry.retainBackfill("smb", rejected))
        assertEquals(1, rejected.closeCount)

        registry.finishReplacement(newestGeneration)
        val retained = FakeSource("retained")
        assertSame(retained, registry.retainBackfill("smb", retained))
        assertEquals(0, retained.closeCount)
    }
}
