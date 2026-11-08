package app.cleanlink

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Publishes "Copy caption" as a sharing shortcut, so the Share menu can show it as a target of its own
 * (next to the people/apps row) instead of only as one more entry under BorraLoco.
 * Whether and where it appears is up to the phone's share sheet.
 */
object CaptionShortcut {
    const val CATEGORY = "com.ahmadhadi.borraloco.category.COPY_CAPTION"
    private const val ID = "copy_caption"

    fun publish(context: Context) {
        val label = context.getString(R.string.caption_label)
        val shortcut = ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_caption_shortcut))
            .setCategories(setOf(CATEGORY))
            .setLongLived(true)
            .setIntent(Intent(context, CaptionActivity::class.java).setAction(Intent.ACTION_DEFAULT))
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }
}
