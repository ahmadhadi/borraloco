package app.cleanlink.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cleanlink.R
import app.cleanlink.UpdateInfo
import app.cleanlink.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** What the update flow is doing right now. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateState
    data class Failed(val info: UpdateInfo) : UpdateState
}

/** Drives the check → prompt → download → install flow. Lives as long as the screen. */
class UpdateController(private val activity: Activity, private val scope: CoroutineScope) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /**
     * Looks for a newer release. [manual] = the user tapped "Check now": then "up to date" and
     * errors are reported; automatic checks stay silent unless there is an update.
     */
    fun check(manual: Boolean) {
        if (state != UpdateState.Idle) return
        state = UpdateState.Checking
        scope.launch {
            state = try {
                val info = Updater.check(activity)
                if (info == null && manual) toast(R.string.update_none)
                info?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            } catch (e: Exception) {
                if (manual) toast(R.string.update_check_failed)
                UpdateState.Idle
            }
        }
    }

    fun download(info: UpdateInfo) {
        state = UpdateState.Downloading(info, 0f)
        scope.launch {
            state = try {
                val apk = Updater.download(activity, info) { p -> state = UpdateState.Downloading(info, p) }
                UpdateState.Ready(info, apk).also { install(it) }
            } catch (e: Exception) {
                UpdateState.Failed(info)
            }
        }
    }

    /** Set while the user is in system settings allowing BorraLoco to install apps. */
    private var awaitingPermission = false

    fun install(ready: UpdateState.Ready) {
        awaitingPermission = !Updater.install(activity, ready.apk)
        if (awaitingPermission) toast(R.string.update_allow_installs)
    }

    /** Call from onResume: continues the install once the user comes back having allowed it. */
    fun onResume() {
        val ready = state as? UpdateState.Ready ?: return
        if (awaitingPermission) install(ready)
    }

    fun dismiss() {
        state = UpdateState.Idle
    }

    private fun toast(res: Int) = Toast.makeText(activity, res, Toast.LENGTH_LONG).show()
}

@Composable
fun UpdateDialog(controller: UpdateController) {
    val context = LocalContext.current
    when (val s = controller.state) {
        UpdateState.Idle, UpdateState.Checking -> Unit

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text(stringResource(R.string.update_available_title, s.info.version)) },
            text = {
                Column {
                    Text(stringResource(R.string.update_available_body, Updater.currentVersion(context)))
                    if (s.info.notes.isNotBlank()) {
                        Text(
                            s.info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { controller.download(s.info) }) {
                    Text(stringResource(R.string.update_download_install))
                }
            },
            dismissButton = {
                TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.update_later)) }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {}, // keep it up until the download finishes
            title = { Text(stringResource(R.string.update_downloading, s.info.version)) },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${(s.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {},
        )

        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text(stringResource(R.string.update_ready_title, s.info.version)) },
            text = { Text(stringResource(R.string.update_ready_body)) },
            confirmButton = {
                TextButton(onClick = { controller.install(s) }) { Text(stringResource(R.string.update_install)) }
            },
            dismissButton = {
                TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.update_later)) }
            },
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text(stringResource(R.string.update_failed_title)) },
            text = { Text(stringResource(R.string.update_failed_body)) },
            confirmButton = {
                TextButton(onClick = { controller.download(s.info) }) { Text(stringResource(R.string.update_retry)) }
            },
            dismissButton = {
                TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.update_later)) }
            },
        )
    }
}
