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
import app.cleanlink.core.HopKind
import app.cleanlink.core.InspectResult
import app.cleanlink.core.StopReason

/** Where a link really goes, hop by hop, and the cleaned final address. [result] null = still following. */
@Composable
fun ExpandView(
    result: InspectResult?,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (result == null) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.expanding))
        }
        return
    }
    val cleaned = result.cleanedFinal.cleaned
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!result.redirected && result.stopped == null) {
            Text(stringResource(R.string.expand_no_redirects), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(R.string.chain_title), style = MaterialTheme.typography.titleSmall)
            result.hops.forEachIndexed { i, hop ->
                Column {
                    Text(
                        stringResource(
                            when {
                                i == 0 -> R.string.hop_start
                                hop.kind == HopKind.UNWRAPPED -> R.string.hop_unwrapped
                                else -> R.string.hop_redirect
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer { Text(hop.url, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        result.stopped?.let {
            Text(
                stringResource(
                    when (it) {
                        StopReason.NOT_A_WEB_LINK -> R.string.stop_not_web
                        StopReason.LOOP -> R.string.stop_loop
                        StopReason.TOO_MANY_HOPS -> R.string.stop_too_many
                        StopReason.PRIVATE_ADDRESS -> R.string.stop_private
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (result.stopped != StopReason.NOT_A_WEB_LINK) {
            Text(stringResource(R.string.clean_destination), style = MaterialTheme.typography.titleSmall)
            SelectionContainer { Text(cleaned, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onCopy(cleaned) }) { Text(stringResource(R.string.copy)) }
                OutlinedButton(onClick = { onOpen(cleaned) }) { Text(stringResource(R.string.open)) }
            }
        }
        Text(
            stringResource(R.string.expand_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
