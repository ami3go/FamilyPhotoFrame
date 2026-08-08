package com.example.familyphotoframe.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familyphotoframe.R
import com.example.familyphotoframe.domain.engine.DisplayPhoto

/** First-run, recovery, and lightweight status surfaces kept outside the render engine. */
@Composable
internal fun FirstRunContent(
    onChoose: () -> Unit,
    onRemote: () -> Unit,
    onSamples: () -> Unit,
) {
    CenteredCard {
        Text(
            text = stringResource(R.string.empty_title),
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.empty_body),
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp).widthIn(max = 520.dp),
        )
        Button(onClick = onChoose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.empty_action_choose))
        }
        OutlinedButton(onClick = onRemote, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.empty_action_remote))
        }
        OutlinedButton(onClick = onSamples, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.empty_action_demo))
        }
    }
}

@Composable
internal fun MessageCard(
    message: String,
    onChoose: () -> Unit,
    onRemote: () -> Unit,
    onSamples: () -> Unit,
) {
    CenteredCard {
        Text(
            text = message,
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp).padding(bottom = 16.dp),
        )
        Button(onClick = onChoose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_choose_folder))
        }
        OutlinedButton(onClick = onRemote, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.empty_action_remote))
        }
        OutlinedButton(onClick = onSamples, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.empty_action_demo))
        }
    }
}

@Composable
private fun CenteredCard(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .background(Color(0xFF14130F), RoundedCornerShape(20.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
internal fun InfoBanner(photo: DisplayPhoto?) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomStart) {
        Column(
            Modifier
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(photo?.fileName.orEmpty(), color = Color.White, fontSize = 16.sp)
            Text(photo?.folderName.orEmpty(), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Text(stringResource(R.string.help_hint), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
internal fun StatusPill(text: String, align: Alignment) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = align) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
