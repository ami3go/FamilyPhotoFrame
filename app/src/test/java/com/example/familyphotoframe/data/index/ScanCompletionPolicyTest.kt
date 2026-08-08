package com.example.familyphotoframe.data.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCompletionPolicyTest {

    @Test
    fun onlyCleanFinishedScanMayReconcile() {
        assertTrue(ScanCompletionPolicy.shouldReconcile(receivedFinished = true, errors = 0))
        assertFalse(ScanCompletionPolicy.shouldReconcile(receivedFinished = true, errors = 1))
        assertFalse(ScanCompletionPolicy.shouldReconcile(receivedFinished = false, errors = 0))
    }

    @Test
    fun missingFinishedEventBecomesAnError() {
        assertEquals(1, ScanCompletionPolicy.finalErrorCount(receivedFinished = false, errors = 0))
        assertEquals(3, ScanCompletionPolicy.finalErrorCount(receivedFinished = false, errors = 3))
        assertEquals(0, ScanCompletionPolicy.finalErrorCount(receivedFinished = true, errors = 0))
    }
}
