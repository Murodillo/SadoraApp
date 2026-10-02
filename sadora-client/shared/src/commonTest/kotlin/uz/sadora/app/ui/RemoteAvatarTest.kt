package uz.sadora.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uz.sadora.app.ui.components.showsPhoto

/**
 * When a face is a photo and when it is initials. A photo needs both a URL and a picture
 * that decoded: no URL, a picture still loading, or bytes that are not an image all fall
 * back — and a photo just removed falls back at once, before the loader catches up.
 */
class RemoteAvatarTest {

    @Test
    fun `a photo shows only with a URL and a decoded picture`() {
        assertTrue(showsPhoto("/v1/doctors/d1/photo?v=1", decoded = true))
        assertFalse(showsPhoto("/v1/doctors/d1/photo?v=1", decoded = false), "loading or broken")
        assertFalse(showsPhoto(null, decoded = true), "removed while the old picture is still held")
        assertFalse(showsPhoto(" ", decoded = true))
        assertFalse(showsPhoto(null, decoded = false))
    }
}
