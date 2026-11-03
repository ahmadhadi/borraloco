package app.cleanlink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.cleanlink.Cleaner
import app.cleanlink.R
import app.cleanlink.core.TextCleanResult
import kotlinx.coroutines.delay

/** Shows the cleaned text, what was removed, and Copy / Share / Open buttons. */
@Composable
fun CleanResultView(
    result: TextCleanResult?,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            result == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.cleaning))
            }
            !result.hasLinks -> Note(Icons.Filled.Info, stringResource(R.string.no_link_found))
            else -> ResultBody(result, onCopy, onShare, onOpen)
        }
    }
}

@Composable
private fun ResultBody(
    result: TextCleanResult,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val context = LocalContext.current
        val removed = result.links.flatMap { it.removed }.distinct()
        Note(
            icon = Icons.Filled.CheckCircle,
            text = Cleaner.summary(context, result),
            emphasis = true,
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    result.cleanedText,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (removed.isNotEmpty()) {
            Text(
                stringResource(R.string.removed_list, removed.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (result.links.any { it.expanded }) Note(Icons.Filled.Info, stringResource(R.string.expanded_note))
        if (result.links.any { it.unwrapped }) Note(Icons.Filled.Info, stringResource(R.string.unwrapped_note))
        if (result.links.any { it.unexpandedShortLink }) Note(Icons.Filled.Warning, stringResource(R.string.short_not_expanded))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onCopy(result.cleanedText) }) { Text(stringResource(R.string.copy)) }
            FilledTonalButton(onClick = { onShare(result.cleanedText) }) {
                Icon(Icons.Filled.Share, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.share))
            }
            // "Open" only makes sense for a single link.
            result.links.singleOrNull()?.let { link ->
                OutlinedButton(onClick = { onOpen(link.cleaned) }) { Text(stringResource(R.string.open)) }
            }
        }
    }
}

@Composable
private fun Note(icon: ImageVector, text: String, emphasis: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = if (emphasis) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = if (emphasis) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A spinner that only appears if work takes longer than [delayMs], to avoid flashing. */
@Composable
fun DelayedProgress(delayMs: Long = 400, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        visible = true
    }
    if (visible) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = modifier) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.cleaning))
            }
        }
    }
}
