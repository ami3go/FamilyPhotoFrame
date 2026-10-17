package com.example.familyphotoframe.ui.slideshow.viewmodel

/** Minutes since local midnight, read fresh from the wall clock on every call. */
internal fun nowMinutes(): Int {
    val c = java.util.Calendar.getInstance()
    return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
}
