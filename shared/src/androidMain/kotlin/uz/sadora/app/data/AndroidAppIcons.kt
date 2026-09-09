package uz.sadora.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
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
 * And nothing happens when the wanted alias is already the enabled one. The call is
 * made after every check-in; without this guard the home screen would redraw daily.
 */
class AndroidAppIcons(context: Context) : AppIcons {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val packageName: String = appContext.packageName

    override fun apply(mood: AppIconMood) {
        val wanted = ComponentName(packageName, aliasFor(mood))
        val alreadyOn = packageManager.getComponentEnabledSetting(wanted) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (alreadyOn) return

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
