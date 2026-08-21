package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class DecodeSuppressionPolicyTest {

    private fun failure(
        stage: DecodeFailureStage = DecodeFailureStage.SOURCE_READ,
        permanent: Boolean = false,
        sourceLevelFailure: Boolean = false,
        exceptionClass: String? = null,
    ) = DecodeFailure(
        photoId = 42L,
        sourceId = "smb",
        fileExtension = "jpg",
        stage = stage,
        exceptionClass = exceptionClass,
        permanent = permanent,
        sourceLevelFailure = sourceLevelFailure,
    )

    @Test
    fun `an ordinary read failure counts against the photo`() {
        assertEquals(
            DecodeSuppressionOutcome.COUNT,
            DecodeSuppressionPolicy.outcomeFor(failure()),
        )
    }

    @Test
    fun `an unsupported format is suppressed permanently`() {
        assertEquals(
            DecodeSuppressionOutcome.PERMANENT,
            DecodeSuppressionPolicy.outcomeFor(
                failure(stage = DecodeFailureStage.CAPABILITY, permanent = true),
            ),
        )
    }

    /** The regression: a NAS outage must not suppress the photos it interrupted. */
    @Test
    fun `a source-level failure is not charged to the photo`() {
        assertEquals(
            DecodeSuppressionOutcome.NONE,
            DecodeSuppressionPolicy.outcomeFor(failure(sourceLevelFailure = true)),
        )
    }

    @Test
    fun `a source-level failure wins even when the failure is marked permanent`() {
        assertEquals(
            DecodeSuppressionOutcome.NONE,
            DecodeSuppressionPolicy.outcomeFor(
                failure(permanent = true, sourceLevelFailure = true),
            ),
        )
    }

    @Test
    fun `heap exhaustion is a process condition, not a property of the photo`() {
        assertEquals(
            DecodeSuppressionOutcome.NONE,
            DecodeSuppressionPolicy.outcomeFor(failure(exceptionClass = "OutOfMemoryError")),
        )
    }

    @Test
    fun `an unrelated exception still counts`() {
        assertEquals(
            DecodeSuppressionOutcome.COUNT,
            DecodeSuppressionPolicy.outcomeFor(failure(exceptionClass = "IOException")),
        )
    }

    /**
     * The sentinel must stay clear of any plausible `temporarilySuppressAfterDecodeFailures`
     * value, or expiring transient suppression would also release permanent ones.
     */
    @Test
    fun `permanent sentinel is far above any configurable threshold`() {
        assertEquals(true, PERMANENT_DECODE_FAILURE_COUNT > 10_000)
    }
}
