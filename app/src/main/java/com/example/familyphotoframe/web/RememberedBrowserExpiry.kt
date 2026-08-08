package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.settings.CustomExpiryUnit
import com.example.familyphotoframe.data.settings.RememberExpiryMode
import com.example.familyphotoframe.data.settings.RememberedBrowserPolicy
import java.util.Calendar

/** Calendar-correct absolute-expiry calculation, compatible with Android 5/API 21. */
object RememberedBrowserExpiry {
    const val MIN_CUSTOM_MS = 10L * 60_000L
    const val HARD_MAX_MS = 10L * 366L * 24L * 60L * 60_000L

    data class Request(
        val mode: RememberExpiryMode,
        val amount: Int? = null,
        val unit: CustomExpiryUnit? = null,
        val confirmForever: Boolean = false,
    )

    fun calculate(nowEpochMs: Long, request: Request, policy: RememberedBrowserPolicy): Long? {
        require(policy.enabled) { "Remembered browsers are disabled on this frame" }
        return when (request.mode) {
            RememberExpiryMode.SESSION_ONLY -> throw IllegalArgumentException("Session-only does not create persistent trust")
            RememberExpiryMode.ONE_HOUR -> bounded(nowEpochMs, nowEpochMs + 60L * 60_000L, policy)
            RememberExpiryMode.ONE_DAY -> bounded(nowEpochMs, nowEpochMs + 24L * 60L * 60_000L, policy)
            RememberExpiryMode.ONE_WEEK -> bounded(nowEpochMs, nowEpochMs + 7L * 24L * 60L * 60_000L, policy)
            RememberExpiryMode.ONE_MONTH -> bounded(nowEpochMs, calendarPlus(nowEpochMs, Calendar.MONTH, 1), policy)
            RememberExpiryMode.ONE_YEAR -> bounded(nowEpochMs, calendarPlus(nowEpochMs, Calendar.YEAR, 1), policy)
            RememberExpiryMode.FOREVER -> {
                require(policy.allowForever) { "Forever trust is disabled by frame policy" }
                require(request.confirmForever) { "Forever trust requires explicit confirmation" }
                null
            }
            RememberExpiryMode.CUSTOM -> custom(nowEpochMs, request, policy)
        }
    }

    private fun custom(now: Long, request: Request, policy: RememberedBrowserPolicy): Long {
        val amount = request.amount ?: throw IllegalArgumentException("Custom duration amount is required")
        val unit = request.unit ?: throw IllegalArgumentException("Custom duration unit is required")
        require(amount > 0) { "Custom duration must be positive" }
        val expiry = when (unit) {
            CustomExpiryUnit.MINUTES -> safeAdd(now, amount.toLong() * 60_000L)
            CustomExpiryUnit.HOURS -> safeAdd(now, amount.toLong() * 60L * 60_000L)
            CustomExpiryUnit.DAYS -> safeAdd(now, amount.toLong() * 24L * 60L * 60_000L)
            CustomExpiryUnit.WEEKS -> safeAdd(now, amount.toLong() * 7L * 24L * 60L * 60_000L)
            CustomExpiryUnit.MONTHS -> calendarPlus(now, Calendar.MONTH, amount)
            CustomExpiryUnit.YEARS -> calendarPlus(now, Calendar.YEAR, amount)
        }
        require(expiry - now >= MIN_CUSTOM_MS) { "Custom duration must be at least 10 minutes" }
        require(expiry - now <= HARD_MAX_MS) { "Custom duration cannot exceed 10 years" }
        return bounded(now, expiry, policy)
    }

    private fun bounded(now: Long, expiry: Long, policy: RememberedBrowserPolicy): Long {
        require(expiry > now) { "Expiry must be in the future" }
        require(expiry - now <= policy.maxExpirySeconds * 1_000L) {
            "The selected expiry exceeds the frame policy"
        }
        return expiry
    }

    private fun calendarPlus(now: Long, field: Int, amount: Int): Long =
        Calendar.getInstance().apply { timeInMillis = now; add(field, amount) }.timeInMillis

    private fun safeAdd(a: Long, b: Long): Long {
        require(b > 0 && a <= Long.MAX_VALUE - b) { "Duration is too large" }
        return a + b
    }
}
