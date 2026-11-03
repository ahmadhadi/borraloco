package app.cleanlink

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cleanlink.core.TextCleanResult
import app.cleanlink.ui.CleanLinkTheme
import app.cleanlink.ui.CleanResultView
import app.cleanlink.ui.DelayedProgress

/**
 * Entry point for links coming from other apps:
 * - Share menu (ACTION_SEND)              → shows a bottom sheet with the clean link
 * - Text selection (ACTION_PROCESS_TEXT)  → replaces the text in place when the field is editable
 * - Link interception (ACTION_VIEW)       → cleans, then opens the link in the real browser/app
 */
class CleanActivity : ComponentActivity() {

    private enum class Mode { SHARE, REPLACE_TEXT, SHOW_TEXT, INTERCEPT }

    private data class Input(val text: String, val mode: Mode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val input = readInput(intent)
        if (input == null) {
            finish()
            return
        }

        setContent {
            CleanLinkTheme {
                var result by remember { mutableStateOf<TextCleanResult?>(null) }
                var showSheet by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    // Show the sheet straight away only when we will end up showing a result;
                    // otherwise just a small spinner if the work takes a moment.
                    val headless = input.mode == Mode.INTERCEPT || input.mode == Mode.REPLACE_TEXT ||
                        AppSettings(this@CleanActivity).copyAndClose
                    if (!headless) showSheet = true
                    val r = Cleaner.cleanText(this@CleanActivity, input.text)
                    if (handleWithoutUi(input, r)) {
                        finish()
                    } else {
                        result = r
                        showSheet = true
                    }
                }

                if (showSheet) {
                    ResultSheet(result)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedProgress() }
                }
            }
        }
    }

    /** Returns true when the result was fully handled and the activity can close. */
    private fun handleWithoutUi(input: Input, r: TextCleanResult): Boolean = when (input.mode) {
        Mode.INTERCEPT -> {
            LinkActions.open(this, r.links.firstOrNull()?.cleaned ?: input.text)
            true
        }
        Mode.REPLACE_TEXT -> {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, r.cleanedText))
            Toast.makeText(this, Cleaner.summary(this, r), Toast.LENGTH_SHORT).show()
            true
        }
        Mode.SHARE, Mode.SHOW_TEXT -> {
            if (AppSettings(this).copyAndClose && r.hasLinks) {
                LinkActions.copy(this, r.cleanedText, toast = Cleaner.summary(this, r))
                true
            } else {
                false
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ResultSheet(result: TextCleanResult?) {
        ModalBottomSheet(
            onDismissRequest = { finish() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
            ) {
                Text(
                    stringResource(R.string.share_target_label),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                CleanResultView(
                    result = result,
                    onCopy = {
                        LinkActions.copy(this@CleanActivity, it)
                        finish()
                    },
                    onShare = {
                        LinkActions.share(this@CleanActivity, it)
                        finish()
                    },
                    onOpen = {
                        LinkActions.open(this@CleanActivity, it)
                        finish()
                    },
                )
            }
        }
    }

    private fun readInput(intent: Intent?): Input? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                text?.let { Input(it, Mode.SHARE) }
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
                text?.let { Input(it, if (readOnly) Mode.SHOW_TEXT else Mode.REPLACE_TEXT) }
            }
            Intent.ACTION_VIEW -> intent.dataString?.let { Input(it, Mode.INTERCEPT) }
            else -> null
        }?.takeIf { it.text.isNotBlank() }
    }
}
