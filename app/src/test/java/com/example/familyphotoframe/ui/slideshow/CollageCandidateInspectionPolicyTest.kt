package com.example.familyphotoframe.ui.slideshow

import org.junit.Assert.assertEquals
import org.junit.Test

class CollageCandidateInspectionPolicyTest {
    @Test fun metadataAlwaysWinsWithoutIo() {
        assertEquals(
            CollageCandidateInspectionAction.USE_METADATA,
            CollageCandidateInspectionPolicy.decide(true, true, 99, 4),
        )
    }

    @Test fun unknownRemoteCandidateIsNeverFetchedForRanking() {
        assertEquals(
            CollageCandidateInspectionAction.SKIP_REMOTE_UNKNOWN,
            CollageCandidateInspectionPolicy.decide(false, true, 0, 4),
        )
    }

    @Test fun localUnknownProbesAreBounded() {
        assertEquals(
            CollageCandidateInspectionAction.PROBE_LOCAL,
            CollageCandidateInspectionPolicy.decide(false, false, 3, 4),
        )
        assertEquals(
            CollageCandidateInspectionAction.SKIP_PROBE_BUDGET,
            CollageCandidateInspectionPolicy.decide(false, false, 4, 4),
        )
    }
}
