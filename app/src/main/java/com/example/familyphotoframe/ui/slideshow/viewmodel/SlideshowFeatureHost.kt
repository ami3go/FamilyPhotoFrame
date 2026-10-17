package com.example.familyphotoframe.ui.slideshow.viewmodel

import android.content.Context
import com.example.familyphotoframe.ServiceLocator
import com.example.familyphotoframe.ui.slideshow.SlideshowUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What a feature area split out of [com.example.familyphotoframe.ui.slideshow.SlideshowViewModel]
 * may use from its owner: the shared services, the application context, the ViewModel's
 * own coroutine scope, and the single UI state it publishes into.
 *
 * Collaborators expose these under the ViewModel's names (`viewModelScope`, `_state`) so
 * their method bodies stay identical to the code they were moved from.
 */
internal class SlideshowFeatureHost(
    val services: ServiceLocator,
    val appContext: Context,
    val viewModelScope: CoroutineScope,
    val state: MutableStateFlow<SlideshowUiState>,
)
