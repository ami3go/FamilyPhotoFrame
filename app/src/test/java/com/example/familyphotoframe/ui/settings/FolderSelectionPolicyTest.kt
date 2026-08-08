package com.example.familyphotoframe.ui.settings

import com.example.familyphotoframe.data.db.FolderSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderSelectionPolicyTest {
    private val folders = listOf(
        FolderSummary("smb", "A", "A", 10),
        FolderSummary("smb", "B", "B", 20),
        FolderSummary("local", "B", "B", 30),
    )

    @Test fun emptySelectionMeansAllFolders() {
        folders.forEach { assertTrue(FolderSelectionPolicy.isSelected(it, emptySet())) }
        assertEquals(3, FolderSelectionPolicy.selectedCount(folders, emptySet()))
    }

    @Test fun deselectingFromAllProducesOneCanonicalSet() {
        val next = FolderSelectionPolicy.toggle(
            folders, emptySet(), folders.first().selectionKey, selected = false,
        )
        assertEquals(folders.drop(1).map { it.selectionKey }.toSet(), next)
        assertFalse(FolderSelectionPolicy.isSelected(folders.first(), next))
    }

    @Test fun selectingEveryFolderNormalizesBackToAll() {
        val oneMissing = folders.dropLast(1).map { it.selectionKey }.toSet()
        val next = FolderSelectionPolicy.toggle(
            folders, oneMissing, folders.last().selectionKey, selected = true,
        )
        assertTrue(next.isEmpty())
    }

    @Test fun legacyFolderNameSelectsEveryMatchingDirectFolder() {
        assertEquals(2, FolderSelectionPolicy.selectedCount(folders, setOf("B")))
        val next = FolderSelectionPolicy.toggle(
            folders, setOf("B"), folders[1].selectionKey, selected = false,
        )
        assertFalse(folders[1].selectionKey in next)
        assertTrue(folders[2].selectionKey in next)
    }
}
