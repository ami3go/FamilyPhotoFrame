package com.example.familyphotoframe.slideshow.shuffle

/** Process-local deduplication that cannot retain deleted scope keys without bound. */
internal class BoundedScopeLogTracker(private val maximumSize: Int) {
    private val lock = Any()
    private val keys = LinkedHashSet<String>()

    fun mark(scopeKey: String): Boolean = synchronized(lock) {
        if (!keys.add(scopeKey)) return@synchronized false
        while (keys.size > maximumSize.coerceAtLeast(1)) {
            val iterator = keys.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        true
    }

    fun forget(scopeKey: String) = synchronized(lock) { keys.remove(scopeKey) }

    val size: Int get() = synchronized(lock) { keys.size }
}
