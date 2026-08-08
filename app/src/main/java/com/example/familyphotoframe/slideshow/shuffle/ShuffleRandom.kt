package com.example.familyphotoframe.slideshow.shuffle

import kotlin.random.Random

interface ShuffleRandom {
    fun nextInt(bound: Int): Int
}

class KotlinShuffleRandom(private val random: Random = Random.Default) : ShuffleRandom {
    override fun nextInt(bound: Int): Int = random.nextInt(bound)
}

class FixedSeedShuffleRandom(seed: Long) : ShuffleRandom {
    private val random = Random(seed)
    override fun nextInt(bound: Int): Int = random.nextInt(bound)
}
