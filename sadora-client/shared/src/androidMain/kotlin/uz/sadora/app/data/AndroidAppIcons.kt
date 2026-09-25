package uz.sadora.app.data

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import uz.sadora.contract.AppIconMood

/**
 * Switches the launcher icon by enabling one of the three aliases in the manifest.
 *
 * Three details in here are the difference between this working and this being a bug
 * report about a disappearing app.
 *
 * The wanted alias is enabled *before* the others are disabled. Doing it the other way
 * round leaves a moment with no enabled LAUNCHER component at all, and a launcher that
 * refreshes in that moment drops the app off the home screen until the next reboot.
 *
 * `DONT_KILL_APP` is passed, or the system restarts the process the instant the change
 * lands — which, since this runs right after the daily check-in, would mean the app
 * closing itself every morning.
 *
 * And the switch waits until no activity is visible. `DONT_KILL_APP` keeps the process,
 * but disabling the alias the running task was launched through still finishes that
 * task — on the S23 signing in as another woman (a different streak, so a different
 * icon) closed the app in her face. So [apply] only remembers the mood; the change
 * happens when the last activity stops, where nobody sees the task go.
 *
 * Nothing happens when the wanted alias is already the enabled one. The call is made
 * after every check-in; without this guard the home screen would redraw daily.
 */
class AndroidAppIcons(context: Context) : AppIcons {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val packageName: String = appContext.packageName

    init {
        Visibility.install(appContext as? Application)
        Visibility.onHidden = { pending?.let { switchTo(it) } }
    }

    override fun apply(mood: AppIconMood) {
        if (isEnabled(mood)) {
            pending = null
            return
        }
        pending = mood
        if (Visibility.started == 0) switchTo(mood)
    }

    private fun isEnabled(mood: AppIconMood): Boolean =
        packageManager.getComponentEnabledSetting(ComponentName(packageName, aliasFor(mood))) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun switchTo(mood: AppIconMood) {
        pending = null
        if (isEnabled(mood)) return
        val wanted = ComponentName(packageName, aliasFor(mood))
        runCatching {
            packageManager.setComponentEnabledSetting(
                wanted,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            AppIconMood.entries
                .filter { it != mood }
                .forEach { other ->
                    packageManager.setComponentEnabledSetting(
                        ComponentName(packageName, aliasFor(other)),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
        }
    }

    companion object {
        /** The mood waiting for the app to leave the screen; process-wide like the aliases. */
        @Volatile private var pending: AppIconMood? = null

        /**
         * Starts counting visible activities. Called first thing in the activity's
         * `onCreate`, before it starts, so the count is right even when the switcher
         * itself is built lazily later.
         */
        fun watch(application: Application) = Visibility.install(application)
    }

    /**
     * Counts started activities once per process. A new [AndroidAppIcons] is made on each
     * activity recreation, so the callbacks must not be registered per instance.
     */
    private object Visibility : Application.ActivityLifecycleCallbacks {
        var started = 0
        var onHidden: () -> Unit = {}
        private var installed = false

        fun install(application: Application?) {
            if (installed || application == null) return
            installed = true
            application.registerActivityLifecycleCallbacks(this)
        }

        override fun onActivityStarted(activity: Activity) { started++ }
        override fun onActivityStopped(activity: Activity) {
            started = (started - 1).coerceAtLeast(0)
            if (started == 0 && !activity.isChangingConfigurations) onHidden()
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    /**
     * The alias each mood maps to. The names are the manifest's and are matched by
     * string, so a rename there has to be a rename here — hence the single place.
     */
    private fun aliasFor(mood: AppIconMood): String = when (mood) {
        AppIconMood.WARM -> "$packageName.Launcher"
        AppIconMood.CALM -> "$packageName.LauncherCalm"
        AppIconMood.COLD -> "$packageName.LauncherCold"
    }
}
