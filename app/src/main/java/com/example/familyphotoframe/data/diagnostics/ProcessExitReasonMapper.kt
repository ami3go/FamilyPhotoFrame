package com.example.familyphotoframe.data.diagnostics

/** Stable mapping; unknown future platform values are deliberately not guessed. */
object ProcessExitReasonMapper {
    fun name(reason: Int): String = when (reason) {
        0 -> "UNKNOWN"
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        13 -> "OTHER"
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "UNKNOWN"
    }

    /** Raw platform descriptions never leave this mapper. */
    fun descriptionCode(description: String?): String {
        val value = description?.lowercase().orEmpty()
        return when {
            value.isBlank() -> "NONE"
            "low memory" in value || "out of memory" in value || "oom" in value -> "LOW_MEMORY"
            "anr" in value || "not responding" in value -> "ANR"
            "native" in value && "crash" in value -> "NATIVE_CRASH"
            "crash" in value || "exception" in value -> "JAVA_CRASH"
            "signal" in value -> "SIGNAL"
            "user" in value -> "USER_ACTION"
            "update" in value -> "PACKAGE_UPDATE"
            else -> "PRESENT_UNCLASSIFIED"
        }
    }
}
