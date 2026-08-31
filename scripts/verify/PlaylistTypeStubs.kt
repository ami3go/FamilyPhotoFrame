enum class TransitionSelectionMode { FIXED, AMBIENT_RANDOM }
enum class TransitionMode { CROSSFADE }
enum class PortraitCollageMode { OFF, AUTOMATIC }

object PlaybackInterval {
    fun clamp(value: Int): Int = value.coerceIn(3, 600)
}
