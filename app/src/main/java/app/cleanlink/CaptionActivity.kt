package app.cleanlink

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cleanlink.core.CaptionResult
import app.cleanlink.core.InstagramCaptionFetcher
import app.cleanlink.core.InstagramCaptions
import app.cleanlink.core.UrlExtractor
import app.cleanlink.ui.CleanLinkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Share target "Copy Instagram caption": share an Instagram post or reel here and its public
 * caption is read from instagram.com, copied to the clipboard and shown.
 */
class CaptionActivity : ComponentActivity() {

    private val fetcher = InstagramCaptionFetcher()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val link = UrlExtractor.extract(text).firstOrNull { InstagramCaptions.parsePost(it) != null }
        if (link == null) {
            Toast.makeText(this, R.string.caption_not_post, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            CleanLinkTheme {
                var result by remember { mutableStateOf<CaptionResult?>(null) }
                LaunchedEffect(Unit) {
                    val r = withContext(Dispatchers.IO) { fetcher.fetch(link) }
                    // Copy at once, so the caption is on the clipboard whatever the user does next.
                    if (r is CaptionResult.Found) {
                        LinkActions.copy(this@CaptionActivity, r.caption, toast = getString(R.string.caption_copied))
                    }
                    result = r
                }
                ModalBottomSheet(
                    onDismissRequest = { finish() },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 24.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.caption_label), style = MaterialTheme.typography.titleLarge)
                        Body(result)
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Body(result: CaptionResult?) {
        when (result) {
            null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.caption_getting))
            }
            is CaptionResult.Found -> {
                Text(
                    stringResource(R.string.caption_copied),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                SelectionContainer { Text(result.caption, style = MaterialTheme.typography.bodyLarge) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { LinkActions.copy(this, result.caption); finish() }) { Text(stringResource(R.string.copy)) }
                    OutlinedButton(onClick = { LinkActions.share(this, result.caption); finish() }) { Text(stringResource(R.string.share)) }
                }
                if (result.truncatedMaybe) Note(R.string.caption_maybe_short)
                Note(R.string.caption_privacy_note)
            }
            CaptionResult.NoCaption -> Message(R.string.caption_none)
            CaptionResult.LoginRequired -> Message(R.string.caption_login)
            CaptionResult.NotAPost -> Message(R.string.caption_not_post)
            CaptionResult.Failed -> Message(R.string.caption_failed)
        }
    }

    @androidx.compose.runtime.Composable
    private fun Message(res: Int) {
        Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
        Note(R.string.caption_privacy_note)
    }

    @androidx.compose.runtime.Composable
    private fun Note(res: Int) {
        Text(stringResource(res), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
