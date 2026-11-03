package app.cleanlink

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.widget.Toast

/** Copy / share / open actions shared by every screen. */
object LinkActions {

    fun copy(context: Context, text: String, toast: String? = null) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clip_label), text))
        // Android 13+ shows its own "Copied" confirmation; only toast on older versions
        // or when we have something more useful to say.
        if (toast != null) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val chooser = Intent.createChooser(send, null)
            // Don't offer CleanLink itself as a share target for an already-clean link.
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownComponents(context))
        start(context, chooser)
    }

    /**
     * Opens [url] outside CleanLink: in the platform's own app when one is the verified handler
     * (Android 11+), otherwise in a browser that isn't CleanLink. Never loops back to us, even when
     * CleanLink is the default browser.
     */
    fun open(context: Context, url: String) {
        val uri = Uri.parse(url)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val app = Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
            try {
                start(context, app, rethrow = true)
                return
            } catch (e: ActivityNotFoundException) {
                // No dedicated app for this link: fall through to a browser.
            }
        }

        val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        val browsers = query(context, view)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
        val default = resolveDefault(context, view)?.activityInfo?.packageName
        val target = default?.takeIf { it in browsers } ?: browsers.singleOrNull()

        when {
            target != null -> start(context, view.setPackage(target))
            browsers.isEmpty() -> Toast.makeText(context, R.string.no_browser, Toast.LENGTH_SHORT).show()
            else -> start(
                context,
                Intent.createChooser(view, null)
                    .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownComponents(context)),
            )
        }
    }

    private fun ownComponents(context: Context): Array<ComponentName> = arrayOf(
        ComponentName(context, CleanActivity::class.java),
        ComponentName(context.packageName, AppSettings.INTERCEPTOR_CLASS),
    )

    private fun start(context: Context, intent: Intent, rethrow: Boolean = false) {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (rethrow) throw e
            Toast.makeText(context, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private fun query(context: Context, intent: Intent): List<ResolveInfo> {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
    }

    private fun resolveDefault(context: Context, intent: Intent): ResolveInfo? {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }
}
