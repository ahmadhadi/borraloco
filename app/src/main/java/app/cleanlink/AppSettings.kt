package app.cleanlink

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit

/** User settings, stored in SharedPreferences. */
class AppSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Follow short links over the network to clean their real destination. */
    var expandShortLinks: Boolean
        get() = prefs.getBoolean(KEY_EXPAND, true)
        set(value) = prefs.edit { putBoolean(KEY_EXPAND, value) }

    /** When sharing to the app, copy the clean link at once instead of showing the sheet. */
    var copyAndClose: Boolean
        get() = prefs.getBoolean(KEY_COPY_AND_CLOSE, false)
        set(value) = prefs.edit { putBoolean(KEY_COPY_AND_CLOSE, value) }

    /** Look for a new release on GitHub each time the app is opened. */
    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_UPDATE, value) }

    /** Opt-in: Inspect also asks Cloudflare's malware-blocking DNS about the site name (never the full link). */
    var onlineSafetyCheck: Boolean
        get() = prefs.getBoolean(KEY_ONLINE_SAFETY, false)
        set(value) = prefs.edit { putBoolean(KEY_ONLINE_SAFETY, value) }

    /**
     * "Clean every link I open": enables the `LinkInterceptor` activity-alias that makes the app
     * show up as a browser. Stored as the component's enabled state, so there is one source of truth.
     */
    var interceptLinks: Boolean
        get() = context.packageManager.getComponentEnabledSetting(interceptor) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        set(value) = context.packageManager.setComponentEnabledSetting(
            interceptor,
            if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )

    private val interceptor: ComponentName
        get() = ComponentName(context.packageName, INTERCEPTOR_CLASS)

    companion object {
        private const val KEY_EXPAND = "expand_short_links"
        private const val KEY_COPY_AND_CLOSE = "copy_and_close"
        private const val KEY_AUTO_UPDATE = "auto_check_updates"
        private const val KEY_ONLINE_SAFETY = "online_safety_check"

        /** The alias is declared relative to the namespace, which can differ from the applicationId. */
        val INTERCEPTOR_CLASS: String =
            CleanActivity::class.java.name.substringBeforeLast('.') + ".LinkInterceptor"
    }
}
