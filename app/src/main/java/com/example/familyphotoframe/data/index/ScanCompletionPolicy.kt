package com.example.familyphotoframe.data.index

/** Pure policy for deciding whether a streamed scan is safe to reconcile. */
object ScanCompletionPolicy {
    fun shouldReconcile(receivedFinished: Boolean, errors: Int): Boolean =
        receivedFinished && errors == 0

    /** A source that simply ends without Finished is itself one scan error. */
    fun finalErrorCount(receivedFinished: Boolean, errors: Int): Int =
        if (receivedFinished) errors else maxOf(1, errors)
}
