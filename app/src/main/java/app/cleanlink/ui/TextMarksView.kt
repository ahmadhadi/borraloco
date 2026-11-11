package app.cleanlink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cleanlink.R
import app.cleanlink.core.MarkFinding
import app.cleanlink.core.MarkKind
import app.cleanlink.core.TextScrubResult

@Composable
private fun label(f: MarkFinding): String = when (f.kind) {
    MarkKind.C2PA_MANIFEST -> stringResource(R.string.mark_c2pa)
    MarkKind.VSR_MARK -> stringResource(R.string.mark_vsr, f.detail.orEmpty())
    MarkKind.HIDDEN_MESSAGE -> stringResource(R.string.mark_message, f.detail.orEmpty())
    MarkKind.INVISIBLE_CHARS -> stringResource(R.string.mark_invisible, f.count)
    MarkKind.ODD_SPACES -> stringResource(R.string.mark_spaces, f.count)
    MarkKind.CHAT_CITATIONS -> stringResource(R.string.mark_citations, f.count)
    MarkKind.KEPT_CHARS -> stringResource(R.string.mark_kept, f.count, f.detail.orEmpty())
}

/**
 * Shown only when the pasted text actually contains hidden marks: what was found, and one button per
 * kind of removal that applies (hidden marks / visible chatbot citations).
 */
@Composable
fun TextMarksCard(
    findings: List<MarkFinding>,
    onRemoveHidden: () -> Unit,
    onRemoveCitations: () -> Unit,
    onRemoveStrict: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hidden = findings.filter { it.kind != MarkKind.CHAT_CITATIONS && it.kind != MarkKind.KEPT_CHARS }
    val kept = findings.filter { it.kind == MarkKind.KEPT_CHARS }
    val citations = findings.filter { it.kind == MarkKind.CHAT_CITATIONS }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.marks_found), style = MaterialTheme.typography.titleSmall)
        }
        for (f in hidden + kept + citations) {
            Text("• " + label(f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hidden.isNotEmpty()) Button(onClick = onRemoveHidden) { Text(stringResource(R.string.remove_hidden)) }
            if (kept.isNotEmpty()) {
                if (hidden.isEmpty()) Button(onClick = onRemoveStrict) { Text(stringResource(R.string.remove_strict)) }
                else OutlinedButton(onClick = onRemoveStrict) { Text(stringResource(R.string.remove_strict)) }
            }
            if (citations.isNotEmpty()) {
                val outlined = hidden.isNotEmpty() || kept.isNotEmpty()
                if (outlined) OutlinedButton(onClick = onRemoveCitations) { Text(stringResource(R.string.remove_citations)) }
                else Button(onClick = onRemoveCitations) { Text(stringResource(R.string.remove_citations)) }
            }
        }
    }
}

/** What a removal did, with Copy / Share for the cleaned text. */
@Composable
fun TextScrubResultView(
    result: TextScrubResult,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.text_cleaned), style = MaterialTheme.typography.titleSmall)
        }
        for (f in result.findings) {
            Text("• " + label(f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onCopy(result.cleaned) }) { Text(stringResource(R.string.copy)) }
            FilledTonalButton(onClick = { onShare(result.cleaned) }) {
                Icon(Icons.Filled.Share, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.share))
            }
        }
    }
}
