package com.example.familyphotoframe.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familyphotoframe.R
import com.example.familyphotoframe.data.settings.ActiveSourceKind
import com.example.familyphotoframe.domain.engine.SourceAvailability
import com.example.familyphotoframe.domain.engine.SourceRole
import com.example.familyphotoframe.domain.engine.SourceStatus
import com.example.familyphotoframe.ui.slideshow.SlideshowUiState
import com.example.familyphotoframe.ui.slideshow.SlideshowViewModel

/**
 * "Which source is actually playing" indicator.
 *
 * The setup forms below it are all equally prominent whether or not a source is in use,
 * which left no way to tell a configured-and-playing NAS from a leftover connection. Each
 * row states role and reachability in words as well as colour: this is read from across a
 * room, and colour alone would be unreadable there and to colour-blind users.
 */
@Composable
internal fun SourceStatusCard(state: SlideshowUiState, vm: SlideshowViewModel) {
    if (state.sourceStatuses.isEmpty()) return
    SettingsSectionCard(stringResource(R.string.settings_source_status)) {
        state.sourceStatuses.forEach { status ->
            SourceStatusRow(status, vm)
        }
    }
}

@Composable
private fun SourceStatusRow(status: SourceStatus, vm: SlideshowViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(status)
            Text(
                sourceKindLabel(status.kind),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = if (status.role == SourceRole.PRIMARY) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Text(
            statusSummary(status),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 22.dp),
        )
        if (status.detail.isNotBlank()) {
            Text(
                status.detail,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 22.dp),
            )
        }
        if (status.canBecomePrimary || status.canAlsoPlay) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 18.dp),
            ) {
                if (status.canBecomePrimary) {
                    OutlinedButton(onClick = { vm.setPrimarySource(status.kind) }) {
                        Text(stringResource(R.string.settings_source_use_primary))
                    }
                }
                if (status.canAlsoPlay) {
                    TextButton(onClick = { vm.setAlsoPlay(status.kind, status.role != SourceRole.ALSO_PLAYING) }) {
                        Text(
                            if (status.role == SourceRole.ALSO_PLAYING) {
                                stringResource(R.string.settings_source_stop_merging)
                            } else {
                                stringResource(R.string.settings_source_also_play)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: SourceStatus) {
    Box(
        Modifier
            .size(12.dp)
            .background(statusColor(status), CircleShape)
    )
}

/** Paired with the text label in [statusSummary]; never the only carrier of meaning. */
@Composable
private fun statusColor(status: SourceStatus): Color = when {
    !status.inPlaybackPool && status.role != SourceRole.FALLBACK ->
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    status.availability == SourceAvailability.OK -> MaterialTheme.colorScheme.primary
    status.availability == SourceAvailability.USING_CACHE -> MaterialTheme.colorScheme.tertiary
    status.availability == SourceAvailability.UNAVAILABLE -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
}

/** One line combining role, reachability and how many photos this source contributes. */
@Composable
internal fun statusSummary(status: SourceStatus): String {
    val role = when (status.role) {
        SourceRole.PRIMARY -> stringResource(R.string.settings_source_role_primary)
        SourceRole.ALSO_PLAYING -> stringResource(R.string.settings_source_role_also)
        SourceRole.CONFIGURED_IDLE -> stringResource(R.string.settings_source_role_idle)
        SourceRole.NOT_CONFIGURED -> stringResource(R.string.settings_source_role_unconfigured)
        SourceRole.FALLBACK -> stringResource(R.string.settings_source_role_fallback)
    }
    if (status.role == SourceRole.NOT_CONFIGURED) return role
    val availability = when (status.availability) {
        SourceAvailability.OK ->
            if (status.inPlaybackPool) stringResource(R.string.settings_source_state_in_use) else null
        SourceAvailability.USING_CACHE -> stringResource(R.string.settings_source_state_cached)
        SourceAvailability.UNAVAILABLE -> stringResource(R.string.settings_source_state_offline)
        SourceAvailability.UNKNOWN -> null
    }
    val photos = pluralStringResource(
        R.plurals.settings_source_photo_count,
        status.indexedPhotos,
        status.indexedPhotos,
    )
    return listOfNotNull(role, availability, photos).joinToString(" · ")
}

/** Compact badge for the top of an individual source's setup card. */
@Composable
internal fun SourceStatusBadge(state: SlideshowUiState, kind: ActiveSourceKind) {
    val status = state.sourceStatuses.firstOrNull { it.kind == kind } ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(status)
        Text(
            statusSummary(status),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
internal fun sourceKindLabel(kind: ActiveSourceKind): String = when (kind) {
    ActiveSourceKind.LOCAL_SAF -> stringResource(R.string.source_kind_local)
    ActiveSourceKind.SMB -> stringResource(R.string.source_kind_smb)
    ActiveSourceKind.SYNOLOGY -> stringResource(R.string.source_kind_synology)
    ActiveSourceKind.WEBDAV -> stringResource(R.string.source_kind_webdav)
    ActiveSourceKind.SAMPLES -> stringResource(R.string.status_source_samples)
    ActiveSourceKind.NONE -> stringResource(R.string.settings_source_role_unconfigured)
}
