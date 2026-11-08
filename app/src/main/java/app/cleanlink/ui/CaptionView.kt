package app.cleanlink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cleanlink.R
import app.cleanlink.core.CaptionPlatform
import app.cleanlink.core.CaptionResult

/** What "Copy caption" found for a post. [result] null = still reading. */
@Composable
fun CaptionView(
    result: CaptionResult?,
    platform: CaptionPlatform,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(if (platform == CaptionPlatform.TIKTOK) R.string.platform_tiktok else R.string.platform_instagram)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (result) {
            null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.caption_getting))
            }
            is CaptionResult.Found -> {
                Text(stringResource(R.string.caption_copied), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                SelectionContainer { Text(result.caption, style = MaterialTheme.typography.bodyLarge) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onCopy(result.caption) }) { Text(stringResource(R.string.copy)) }
                    OutlinedButton(onClick = { onShare(result.caption) }) { Text(stringResource(R.string.share)) }
                }
                if (result.truncatedMaybe) Note(stringResource(R.string.caption_maybe_short))
                Note(stringResource(R.string.caption_privacy_note, name))
            }
            CaptionResult.NoCaption -> Message(stringResource(R.string.caption_none), name)
            CaptionResult.LoginRequired -> Message(stringResource(R.string.caption_login, name), name)
            CaptionResult.NotAPost -> Message(stringResource(R.string.caption_not_post), name)
            CaptionResult.Failed -> Message(stringResource(R.string.caption_failed, name), name)
        }
    }
}

@Composable
private fun Message(text: String, platform: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
    Note(stringResource(R.string.caption_privacy_note, platform))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
