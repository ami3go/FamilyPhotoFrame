package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These rules were extracted from SlideshowViewModel precisely so they could be executed
 * rather than only read. Mirrors scripts/verify/SourcePoolPolicyChecks.kt.
 */
class SourcePoolPolicyTest {

    @Test fun healthySourcesAllPlayInOrder() {
        val plan = SourcePoolPolicy.initialPlan(listOf(
            SourcePoolPolicy.Slot("local_saf", healthy = true, isChosen = true),
            SourcePoolPolicy.Slot("smb", healthy = true),
        ))
        assertEquals(listOf("local_saf", "smb"), (plan as SourcePoolPolicy.Plan.Play).primaryIds)
    }

    /** The point of merged pools: one source failing must not stop the others. */
    @Test fun unhealthyCoPrimaryIsDroppedNotFatal() {
        val plan = SourcePoolPolicy.initialPlan(listOf(
            SourcePoolPolicy.Slot("local_saf", healthy = true, isChosen = true),
            SourcePoolPolicy.Slot("smb", healthy = false),
        ))
        assertEquals(listOf("local_saf"), (plan as SourcePoolPolicy.Plan.Play).primaryIds)
    }

    @Test fun everythingDownDefersToTheChosenSource() {
        val plan = SourcePoolPolicy.initialPlan(listOf(
            SourcePoolPolicy.Slot("webdav", healthy = false),
            SourcePoolPolicy.Slot("smb", healthy = false, isChosen = true),
        ))
        assertEquals("smb", (plan as SourcePoolPolicy.Plan.Unreachable).sourceId)
    }

    @Test fun nothingConfiguredIsItsOwnPlan() {
        assertTrue(SourcePoolPolicy.initialPlan(emptyList()) is SourcePoolPolicy.Plan.NothingConfigured)
    }

    /** Regression: a recovering source must rejoin the pool, not replace it. */
    @Test fun promotedSourceJoinsExistingCoPrimaries() {
        val pool = SourcePoolPolicy.afterPromote(listOf("local_saf"), "smb")
        assertEquals(listOf("local_saf", "smb"), pool)
        assertEquals(pool, (SourcePoolPolicy.planFor(pool, "smb") as SourcePoolPolicy.Plan.Play).primaryIds)
    }

    @Test fun promoteIsIdempotentAndDemoteIsTargeted() {
        assertEquals(listOf("smb"), SourcePoolPolicy.afterPromote(listOf("smb"), "smb"))
        assertEquals(listOf("local_saf"), SourcePoolPolicy.afterDemote(listOf("local_saf", "smb"), "smb"))
        assertEquals(listOf("local_saf"), SourcePoolPolicy.afterDemote(listOf("local_saf"), "smb"))
    }

    @Test fun losingTheLastSourceFallsBackToUnreachable() {
        val pool = SourcePoolPolicy.afterDemote(listOf("smb"), "smb")
        assertEquals("smb", (SourcePoolPolicy.planFor(pool, "smb") as SourcePoolPolicy.Plan.Unreachable).sourceId)
    }
}
