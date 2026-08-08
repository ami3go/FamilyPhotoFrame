package com.example.familyphotoframe.ui.slideshow

import com.example.familyphotoframe.domain.engine.DecodeFailure

/** Result of resolving an indexed photo into a model Coil can decode. */
sealed interface PhotoModelResolution {
    data class Ready(val model: Any) : PhotoModelResolution
    data class Failed(val failure: DecodeFailure) : PhotoModelResolution
}
