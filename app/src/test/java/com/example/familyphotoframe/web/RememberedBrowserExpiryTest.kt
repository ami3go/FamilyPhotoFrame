package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.settings.CustomExpiryUnit
import com.example.familyphotoframe.data.settings.RememberExpiryMode
import com.example.familyphotoframe.data.settings.RememberedBrowserPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RememberedBrowserExpiryTest {
    private lateinit var previousZone: TimeZone
    private val policy = RememberedBrowserPolicy(
        enabled = true,
        maxExpirySeconds = 366L * 24L * 60L * 60L,
        allowForever = true,
    )

    @Before fun useUtc() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() { TimeZone.setDefault(previousZone) }

    @Test fun oneMonthUsesCalendarArithmeticAtMonthEnd() {
        val start = utc(2024, Calendar.JANUARY, 31)
        val result = RememberedBrowserExpiry.calculate(
            start,
            RememberedBrowserExpiry.Request(RememberExpiryMode.ONE_MONTH),
            policy,
        )!!
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = result }
        assertEquals(2024, c.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH))
        assertEquals(29, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun oneYearHandlesLeapDay() {
        val start = utc(2024, Calendar.FEBRUARY, 29)
        val result = RememberedBrowserExpiry.calculate(
            start,
            RememberedBrowserExpiry.Request(RememberExpiryMode.ONE_YEAR),
            policy,
        )!!
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = result }
        assertEquals(2025, c.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH))
        assertEquals(28, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun foreverRequiresConfirmation() {
        assertThrows(IllegalArgumentException::class.java) {
            RememberedBrowserExpiry.calculate(
                1_000_000L,
                RememberedBrowserExpiry.Request(RememberExpiryMode.FOREVER, confirmForever = false),
                policy,
            )
        }
        assertNull(
            RememberedBrowserExpiry.calculate(
                1_000_000L,
                RememberedBrowserExpiry.Request(RememberExpiryMode.FOREVER, confirmForever = true),
                policy,
            )
        )
    }

    @Test fun customDurationRejectsLessThanTenMinutes() {
        assertThrows(IllegalArgumentException::class.java) {
            RememberedBrowserExpiry.calculate(
                1_000_000L,
                RememberedBrowserExpiry.Request(
                    RememberExpiryMode.CUSTOM,
                    amount = 9,
                    unit = CustomExpiryUnit.MINUTES,
                ),
                policy,
            )
        }
    }

    private fun utc(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }.timeInMillis
}
