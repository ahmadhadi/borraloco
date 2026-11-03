package app.cleanlink

import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.cleanlink.ui.CleanLinkTheme
import app.cleanlink.ui.DelayedProgress
import kotlinx.coroutines.launch

/**
 * Cleans the link on the clipboard and puts the clean version back.
 *
 * Since Android 10 only the focused app may read the clipboard, so the Quick Settings tile opens
 * this invisible activity and the clipboard is read once the window has focus.
 */
class ClipboardCleanActivity : ComponentActivity() {
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CleanLinkTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedProgress() }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || started) return
        started = true

        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()

        if (text.isNullOrBlank()) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val result = Cleaner.cleanText(this@ClipboardCleanActivity, text)
            val message = Cleaner.summary(this@ClipboardCleanActivity, result)
            if (result.changed) {
                LinkActions.copy(this@ClipboardCleanActivity, result.cleanedText, toast = message)
            } else {
                Toast.makeText(this@ClipboardCleanActivity, message, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
