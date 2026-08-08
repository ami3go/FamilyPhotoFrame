package com.example.familyphotoframe.data.index

import com.example.familyphotoframe.data.source.SourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifScanDecisionTest {
    @Test fun localOnlyReadsExactlyLocalSourceTypes() {
        SourceType.entries.forEach { type ->
            if (type.isLocal) {
                assertTrue(ExifScanDecision.shouldRead(ExifScanPolicy.LOCAL_ONLY, type))
            } else {
                assertFalse(ExifScanDecision.shouldRead(ExifScanPolicy.LOCAL_ONLY, type))
            }
        }
        assertFalse(ExifScanDecision.shouldRead(ExifScanPolicy.LOCAL_ONLY, SourceType.SMB_SOURCE))
        assertFalse(ExifScanDecision.shouldRead(ExifScanPolicy.LOCAL_ONLY, SourceType.SYNOLOGY_FILE_STATION))
        assertFalse(ExifScanDecision.shouldRead(ExifScanPolicy.LOCAL_ONLY, SourceType.WEBDAV))
    }

    @Test fun explicitPoliciesOverrideLocality() {
        SourceType.entries.forEach { type ->
            assertTrue(ExifScanDecision.shouldRead(ExifScanPolicy.ALL_SOURCES, type))
            assertFalse(ExifScanDecision.shouldRead(ExifScanPolicy.NEVER, type))
        }
    }
}
