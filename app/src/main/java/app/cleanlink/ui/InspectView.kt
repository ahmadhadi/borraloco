package app.cleanlink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cleanlink.InspectOutcome
import app.cleanlink.R
import app.cleanlink.core.Finding
import app.cleanlink.core.FindingCode
import app.cleanlink.core.HopKind
import app.cleanlink.core.StopReason
import app.cleanlink.core.Verdict

/** Shows where a link leads (every hop) and the warning signs found. [outcome] null = still working. */
@Composable
fun InspectView(
    outcome: InspectOutcome?,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (outcome == null) {
        Row(modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.inspecting))
        }
        return
    }
    val verdict = outcome.safety.verdict
    val cleaned = outcome.chain.cleanedFinal.cleaned
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val (title, container, onContainer) = when (verdict) {
            Verdict.DANGER -> Triple(R.string.verdict_danger, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            Verdict.CAUTION -> Triple(R.string.verdict_caution, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            Verdict.NO_WARNINGS -> Triple(R.string.verdict_none, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Surface(color = container, contentColor = onContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                outcome.safety.findings.forEach { Text("• " + findingText(it), style = MaterialTheme.typography.bodyMedium) }
            }
        }

        Text(stringResource(R.string.chain_title), style = MaterialTheme.typography.titleSmall)
        outcome.chain.hops.forEachIndexed { i, hop ->
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
        outcome.chain.stopped?.let {
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

        if (outcome.chain.stopped != StopReason.NOT_A_WEB_LINK) {
            Text(stringResource(R.string.clean_destination), style = MaterialTheme.typography.titleSmall)
            SelectionContainer { Text(cleaned, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onCopy(cleaned) }) { Text(stringResource(R.string.copy)) }
                // No one-tap "Open" on a link we flagged as dangerous.
                if (verdict != Verdict.DANGER) {
                    OutlinedButton(onClick = { onOpen(cleaned) }) { Text(stringResource(R.string.open)) }
                }
            }
        }

        Text(
            stringResource(if (outcome.checkedOnline) R.string.inspect_note_online else R.string.inspect_note_network),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.inspect_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun findingText(f: Finding): String = stringResource(findingRes(f.code), f.detail)

private fun findingRes(code: FindingCode): Int = when (code) {
    FindingCode.NOT_A_LINK -> R.string.finding_not_a_link
    FindingCode.DANGEROUS_SCHEME -> R.string.finding_dangerous_scheme
    FindingCode.INTENT_LINK -> R.string.finding_intent_link
    FindingCode.APP_LINK -> R.string.finding_app_link
    FindingCode.NO_HTTPS -> R.string.finding_no_https
    FindingCode.USERINFO_IN_LINK -> R.string.finding_userinfo_in_link
    FindingCode.IP_ADDRESS -> R.string.finding_ip_address
    FindingCode.OBFUSCATED_IP -> R.string.finding_obfuscated_ip
    FindingCode.PRIVATE_ADDRESS -> R.string.finding_private_address
    FindingCode.ENCODED_HOST -> R.string.finding_encoded_host
    FindingCode.LOOKALIKE_CHARACTERS -> R.string.finding_lookalike_characters
    FindingCode.INTERNATIONAL_DOMAIN -> R.string.finding_international_domain
    FindingCode.BRAND_LOOKALIKE -> R.string.finding_brand_lookalike
    FindingCode.BRAND_PHISHING_WORDS -> R.string.finding_brand_phishing_words
    FindingCode.BRAND_TYPO -> R.string.finding_brand_typo
    FindingCode.SCAM_TLD -> R.string.finding_scam_tld
    FindingCode.FILE_TLD -> R.string.finding_file_tld
    FindingCode.TOO_MANY_SUBDOMAINS -> R.string.finding_too_many_subdomains
    FindingCode.LONG_HOST -> R.string.finding_long_host
    FindingCode.MANY_HYPHENS -> R.string.finding_many_hyphens
    FindingCode.ODD_PORT -> R.string.finding_odd_port
    FindingCode.PROGRAM_DOWNLOAD -> R.string.finding_program_download
    FindingCode.SHORT_LINK -> R.string.finding_short_link
    FindingCode.REDIRECT_WRAPPER -> R.string.finding_redirect_wrapper
    FindingCode.DNS_BLOCKED -> R.string.finding_dns_blocked
}
