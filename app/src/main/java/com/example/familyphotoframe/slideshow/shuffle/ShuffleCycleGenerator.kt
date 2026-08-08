package com.example.familyphotoframe.slideshow.shuffle

/** Pure Fisher-Yates cycle generation. Persisted order, not the random seed, is authoritative. */
object ShuffleCycleGenerator {
    fun <T> generate(
        input: Collection<T>,
        previousLast: T?,
        random: ShuffleRandom,
    ): List<T> {
        val values = input.distinct().toMutableList()
        for (index in values.lastIndex downTo 1) {
            val swap = random.nextInt(index + 1)
            if (swap != index) {
                val temporary = values[index]
                values[index] = values[swap]
                values[swap] = temporary
            }
        }
        if (values.size > 1 && previousLast != null && values.first() == previousLast) {
            val replacement = values.indexOfFirst { it != previousLast }
            if (replacement > 0) {
                val temporary = values[0]
                values[0] = values[replacement]
                values[replacement] = temporary
            }
        }
        return values
    }

    /** Inserts new members at random positions among the unresolved suffix, exactly once. */
    fun <T> mergeRemaining(
        existingRemaining: List<T>,
        newlyEligible: Collection<T>,
        random: ShuffleRandom,
    ): List<T> {
        val result = existingRemaining.distinct().toMutableList()
        newlyEligible.distinct().filterNot { it in result }.forEach { item ->
            result.add(random.nextInt(result.size + 1), item)
        }
        return result
    }
}
