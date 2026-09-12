package uz.sadora.app

import uz.sadora.app.nav.AppLinks

/**
 * The Swift side hands URLs in here. A tiny object rather than a direct call into the
 * `nav` package, because Kotlin/Native exports top-level objects to Swift cleanly and
 * the app should not have to know the package layout of the shared module.
 */
object IosAppLinks {
    fun offer(url: String) = AppLinks.offer(url)
}
