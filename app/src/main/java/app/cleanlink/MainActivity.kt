package app.cleanlink

import android.app.StatusBarManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cleanlink.core.TextCleanResult
import app.cleanlink.ui.CleanLinkTheme
import app.cleanlink.core.CaptionFetcher
import app.cleanlink.core.CaptionResult
import app.cleanlink.core.UrlExtractor
import app.cleanlink.ui.CaptionView
import app.cleanlink.ui.CleanResultView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.cleanlink.ui.UpdateController
import app.cleanlink.ui.UpdateDialog
import app.cleanlink.ui.UpdateState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var updates: UpdateController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        updates = UpdateController(this, lifecycleScope)
        // Check once per launch (not again on rotation).
        if (savedInstanceState == null && AppSettings(this).autoCheckUpdates) updates.check(manual = false)

        setContent {
            CleanLinkTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HomeScreen(updates)
                    UpdateDialog(updates)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updates.onResume()
    }
}

@Composable
private fun HomeScreen(updates: UpdateController) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<TextCleanResult?>(null) }
    var cleaning by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf<CaptionResult?>(null) }
    var captioning by remember { mutableStateOf(false) }
    val captionFetcher = remember { CaptionFetcher() }
    // The first Instagram / TikTok post link in the box, if any: that is what "Copy caption" reads.
    val captionLink = remember(input) { UrlExtractor.extract(input).firstOrNull { CaptionFetcher.platformOf(it) != null } }

    fun copyCaption() {
        val link = captionLink ?: return
        captioning = true
        caption = null
        result = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { captionFetcher.fetch(link) }
            // Copy at once, so the caption is on the clipboard whatever happens next.
            if (r is CaptionResult.Found) LinkActions.copy(context, r.caption, toast = context.getString(R.string.caption_copied))
            caption = r
        }
    }

    fun clean() {
        if (input.isBlank()) return
        cleaning = true
        result = null
        captioning = false
        scope.launch {
            result = Cleaner.cleanText(context, input)
            cleaning = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Manual cleaning ----
        Section(stringResource(R.string.clean_a_link)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; result = null; captioning = false },
                label = { Text(stringResource(R.string.input_label)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!text.isNullOrBlank()) {
                        input = text
                        clean()
                    }
                }) { Text(stringResource(R.string.paste_and_clean)) }
                Button(onClick = { clean() }, enabled = input.isNotBlank() && !cleaning) {
                    Text(stringResource(R.string.clean))
                }
                if (input.isNotEmpty()) {
                    TextButton(onClick = { input = ""; result = null; captioning = false }) { Text(stringResource(R.string.clear)) }
                }
            }
            if (captionLink != null) {
                OutlinedButton(onClick = { copyCaption() }, enabled = !captioning || caption != null) {
                    Text(stringResource(R.string.caption_label))
                }
            }
            if (captioning) {
                HorizontalDivider()
                CaptionView(
                    result = caption,
                    platform = CaptionFetcher.platformOf(captionLink ?: "") ?: app.cleanlink.core.CaptionPlatform.INSTAGRAM,
                    onCopy = { LinkActions.copy(context, it) },
                    onShare = { LinkActions.share(context, it) },
                )
            } else if (cleaning || result != null) {
                HorizontalDivider()
                CleanResultView(
                    result = result,
                    onCopy = { LinkActions.copy(context, it) },
                    onShare = { LinkActions.share(context, it) },
                    onOpen = { LinkActions.open(context, it) },
                )
            }
        }

        // ---- How to use ----
        Section(stringResource(R.string.ways_title)) {
            Way(stringResource(R.string.way_share_title), stringResource(R.string.way_share_body))
            Way(stringResource(R.string.way_select_title), stringResource(R.string.way_select_body))
            Way(stringResource(R.string.way_tile_title), stringResource(R.string.way_tile_body))
            Way(stringResource(R.string.way_caption_title), stringResource(R.string.way_caption_body))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val tileLabel = stringResource(R.string.tile_label)
                OutlinedButton(onClick = {
                    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                        ComponentName(context, CleanClipboardTileService::class.java),
                        tileLabel,
                        Icon.createWithResource(context, R.drawable.ic_tile),
                        context.mainExecutor,
                    ) { }
                }) { Text(stringResource(R.string.add_tile)) }
            }
        }

        // ---- Settings ----
        Section(stringResource(R.string.settings_title)) {
            var expand by remember { mutableStateOf(settings.expandShortLinks) }
            var copyAndClose by remember { mutableStateOf(settings.copyAndClose) }
            var intercept by remember { mutableStateOf(settings.interceptLinks) }

            SettingSwitch(
                title = stringResource(R.string.setting_expand_title),
                body = stringResource(R.string.setting_expand_body),
                checked = expand,
                onChange = { expand = it; settings.expandShortLinks = it },
            )
            SettingSwitch(
                title = stringResource(R.string.setting_copy_title),
                body = stringResource(R.string.setting_copy_body),
                checked = copyAndClose,
                onChange = { copyAndClose = it; settings.copyAndClose = it },
            )
            SettingSwitch(
                title = stringResource(R.string.setting_intercept_title),
                body = stringResource(R.string.setting_intercept_body),
                checked = intercept,
                onChange = { intercept = it; settings.interceptLinks = it },
            )
            if (intercept) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                }) { Text(stringResource(R.string.open_default_apps)) }
            }

            HorizontalDivider()
            var autoUpdate by remember { mutableStateOf(settings.autoCheckUpdates) }
            SettingSwitch(
                title = stringResource(R.string.setting_updates_title),
                body = stringResource(R.string.setting_updates_body),
                checked = autoUpdate,
                onChange = { autoUpdate = it; settings.autoCheckUpdates = it },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.current_version, remember { Updater.currentVersion(context) }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val checking = updates.state == UpdateState.Checking
                OutlinedButton(onClick = { updates.check(manual = true) }, enabled = !checking) {
                    Text(stringResource(if (checking) R.string.update_checking else R.string.update_check_now))
                }
            }
        }

        // ---- Supported platforms ----
        Section(stringResource(R.string.supported_title)) {
            Text(
                Cleaner.supportedPlatforms.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.supported_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Way(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitch(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
