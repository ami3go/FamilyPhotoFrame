package com.example.familyphotoframe.slideshow.shuffle

import com.example.familyphotoframe.data.settings.SelectionMode
import com.example.familyphotoframe.util.toHexString
import java.security.MessageDigest

/** Builds stable scope/revision identities from eligibility-only inputs. */
object ShuffleScopeKeyFactory {
    data class EligibilityInputs(
        val playlistId: String,
        val sourceIds: Collection<String>,
        val selectedFolders: Collection<String>,
        val favoritesOnly: Boolean,
        /** Runtime cache availability is not an eligibility identity. */
        val cachedOnly: Boolean = false,
        val maxDecodeFailures: Int,
        val localUploadsOnly: Boolean = false,
        val hiddenPhotosIncluded: Boolean = false,
        val dateFilterSignature: String = "",
        val formatCapabilitySignature: String = "",
    )

    fun eligibilityRevision(inputs: EligibilityInputs): Long {
        val canonical = buildString {
            append("playlist=").append(inputs.playlistId.trim()).append('\n')
            append("sources=").append(inputs.sourceIds.map(String::trim).filter(String::isNotEmpty).sorted().joinToString(",")).append('\n')
            append("folders=").append(inputs.selectedFolders.map(String::trim).filter(String::isNotEmpty).sorted().joinToString(",")).append('\n')
            append("favorites=").append(inputs.favoritesOnly).append('\n')
            append("maxFailures=").append(inputs.maxDecodeFailures).append('\n')
            append("localUploads=").append(inputs.localUploadsOnly).append('\n')
            append("hiddenIncluded=").append(inputs.hiddenPhotosIncluded).append('\n')
            append("date=").append(inputs.dateFilterSignature.trim()).append('\n')
            append("formats=").append(inputs.formatCapabilitySignature.trim())
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        var value = 0L
        for (index in 0 until 8) value = (value shl 8) or (digest[index].toLong() and 0xffL)
        return value and Long.MAX_VALUE
    }

    fun scope(
        playlistId: String,
        revision: Long,
        playbackOrder: SelectionMode,
        poolRole: String,
    ): ShuffleScopeDescriptor {
        val raw = "${playlistId.trim()}|$revision|${playbackOrder.name}|${poolRole.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val suffix = digest.toHexString().substring(0, 24)
        return ShuffleScopeDescriptor(
            scopeKey = "shuffle:$suffix",
            playlistId = playlistId.trim(),
            eligibilityRevision = revision,
            poolRole = poolRole.trim(),
        )
    }
}
