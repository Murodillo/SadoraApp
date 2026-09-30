package uz.sadora.app.data

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.app.model.AppState
import uz.sadora.app.model.DoctorFilter
import uz.sadora.app.model.DoctorSort
import uz.sadora.contract.DoctorListItem
import uz.sadora.contract.DoctorSpecialty

/**
 * The directory as the controller keeps it: the server's list in the server's order,
 * a quiet read that fails leaves no banner anywhere, a loud one leaves it on the
 * directory and not on the doctor page, and no backend is an empty list, not a spinner.
 */
class DoctorDirectoryControllerTest {

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private val list = listOf(
        DoctorListItem("d2", "Dr. Nodira Karimova", DoctorSpecialty.GYNECOLOGIST, "Klinika 1", 12, priceMinor = 5_000_000),
        DoctorListItem("d1", "Dr. Malika Aliyeva", DoctorSpecialty.PSYCHOLOGIST, "Klinika 2", 4, onlineNow = true),
    )

    @Test
    fun `the list is read in the server's order and filtered where it is kept`() = runTest {
        val recording = RecordingEngine { request ->
            if (request.url.encodedPath == "/v1/doctors") json(encode(list)) else json("{}", HttpStatusCode.NotFound)
        }
        val doctors = graph(recording).doctorController(AppState())

        doctors.loadDirectory()
        assertTrue(doctors.directoryLoaded)
        assertEquals(listOf("d2", "d1"), doctors.arrangedDirectory.map { it.id })

        doctors.directoryFilter = DoctorFilter(freeOnly = true)
        assertEquals(listOf("d1"), doctors.arrangedDirectory.map { it.id })
        doctors.directorySort = DoctorSort.Price
        doctors.resetDirectoryFilter()
        assertEquals(listOf("d1", "d2"), doctors.arrangedDirectory.map { it.id }, "the sort stays when the filter is cleared")
    }

    @Test
    fun `a failed read is quiet behind a card and loud only on the directory`() = runTest {
        val recording = RecordingEngine { json("{}", HttpStatusCode.InternalServerError) }
        val doctors = graph(recording).doctorController(AppState())

        doctors.loadDirectory(quiet = true)
        assertFalse(doctors.directoryLoaded)
        assertNull(doctors.directoryCalls.error, "Bugun's card simply stays hidden")

        doctors.loadDirectory()
        assertNotNull(doctors.directoryCalls.error)
        assertNull(doctors.error, "the doctor page's banner is its own")
        assertTrue(doctors.directory.isEmpty())
    }

    @Test
    fun `without a backend the directory is empty rather than loading forever`() = runTest {
        val doctors = DoctorController(null, AppState())
        doctors.loadDirectory()
        assertTrue(doctors.directoryLoaded)
        assertTrue(doctors.directory.isEmpty())
    }
}
