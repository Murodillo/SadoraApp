package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import uz.sadora.app.data.api.CommunityApi
import uz.sadora.app.data.api.DoctorApi
import uz.sadora.app.data.api.NotificationApi
import uz.sadora.app.model.AppState
import uz.sadora.app.model.DoctorSort
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityProfile
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.DoctorListItem
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorReview
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationMessage
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.Page

/**
 * The lists that used to stop at the server's cap now read on. What is pinned: the
 * doctor directory reads by offset, a refresh keeps the pages, and a sort reads the
 * rest first; her reviews and a doctor's or an alias's posts read on by offset past what
 * the page carried; and the notification history reads below the last row it read,
 * shown or not.
 */
class ListPagingControllerTest {

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private fun doctor(n: Int) = DoctorListItem("d$n", "Dr. $n", DoctorSpecialty.GYNECOLOGIST, "Klinika", 5, priceMinor = (100 - n).toLong())

    private fun post(id: String, minutesAgo: Int) = CommunityPost(
        id = id,
        topic = CommunityTopic.WELLBEING,
        alias = "Yorug' Tong",
        tint = 1,
        body = "Post $id",
        createdAt = TestNow - minutesAgo.minutes,
        likeCount = 0,
        commentCount = 0,
    )

    /** A page of [all] as the server cuts it, by the request's limit and offset. */
    private fun <T> slice(all: List<T>, request: io.ktor.client.request.HttpRequestData): List<T> {
        val offset = request.url.parameters["offset"]?.toInt() ?: 0
        val limit = request.url.parameters["limit"]!!.toInt()
        return all.drop(offset).take(limit)
    }

    @Test
    fun `the directory reads on by offset, a refresh keeps the pages, and a sort reads the rest`() = runTest {
        val page = DoctorApi.DIRECTORY_PAGE
        val all = (0 until page * 2 + 5).map { doctor(it) }
        val asked = mutableListOf<String>()
        val recording = RecordingEngine { request ->
            asked.add(request.url.encodedQuery)
            json(encode(slice(all, request)))
        }
        val doctors = graph(recording).doctorController(AppState())

        doctors.loadDirectory()
        assertEquals(page, doctors.directory.size)
        assertTrue(doctors.directoryHasMore)

        doctors.loadMoreDirectory()
        assertEquals("limit=$page&offset=$page", asked.last())
        assertEquals(page * 2, doctors.directory.size)

        doctors.loadDirectory(quiet = true)
        assertEquals("limit=${page * 2}&offset=0", asked.last(), "the refresh re-reads what she had scrolled to")
        assertEquals(page * 2, doctors.directory.size)
        assertTrue(doctors.directoryHasMore)

        // The cheapest doctor is on the last page: ordering by price reads it first.
        doctors.directorySort = DoctorSort.Price
        doctors.loadWholeDirectory()
        assertFalse(doctors.directoryHasMore)
        assertEquals(all.map { it.id }, doctors.directory.map { it.id })
        assertEquals(all.last().id, doctors.arrangedDirectory.first().id)
    }

    @Test
    fun `her reviews read on by offset and a second open keeps them`() = runTest {
        val page = DoctorApi.REVIEW_PAGE
        val all = (0 until page + 3).map { DoctorReview(rating = 5, review = "r$it", createdAt = TestNow - it.minutes) }
        val offsets = mutableListOf<String?>()
        val recording = RecordingEngine { request ->
            offsets.add(request.url.parameters["offset"])
            json(encode(slice(all, request)))
        }
        val doctors = graph(recording).doctorController(AppState())

        doctors.loadReviews("d1")
        assertEquals(page, doctors.reviews.size)
        assertTrue(doctors.reviewsHasMore)

        doctors.loadMoreReviews()
        assertEquals(page.toString(), offsets.last())
        assertEquals(all, doctors.reviews)
        assertFalse(doctors.reviewsHasMore)

        doctors.loadReviews("d1")
        assertEquals(all, doctors.reviews, "the reload did not fold the list back to its first page")
        assertFalse(doctors.reviewsHasMore)
    }

    @Test
    fun `a doctor's page reads her older posts and drops the overlap`() = runTest {
        val all = (0 until 25).map { post("p$it", it) }
        val offsets = mutableListOf<Int>()
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            if (path.endsWith("/posts")) {
                // She posted in between: the second page starts one earlier.
                val offset = request.url.parameters["offset"]!!.toInt().also(offsets::add)
                val items = all.drop(offset - 1).take(request.url.parameters["limit"]!!.toInt())
                json(encode(Page(items, all.size.toLong(), DoctorApi.POSTS_PAGE, offset - 1)))
            } else {
                json(encode(DoctorProfile("d1", "Dr. 1", DoctorSpecialty.GYNECOLOGIST, "Klinika", 5, verifiedSince = TestNow, postCount = all.size, posts = all.take(20))))
            }
        }
        val doctors = graph(recording).doctorController(AppState())

        doctors.loadProfile("d1")
        assertEquals(20, doctors.profilePosts.size)
        assertTrue(doctors.profilePostsHasMore)

        doctors.loadMoreProfilePosts()
        assertEquals(listOf(20), offsets)
        assertEquals(all.map { it.id }, doctors.profilePosts.map { it.id }, "no post twice")
        assertFalse(doctors.profilePostsHasMore)

        doctors.loadProfile("d1")
        assertEquals(all.size, doctors.profilePosts.size, "a refresh keeps the older posts under the page")
    }

    @Test
    fun `an alias page reads her older posts and a refresh keeps them`() = runTest {
        val page = CommunityApi.PROFILE_POSTS_PAGE
        val all = (0 until page + 4).map { post("a$it", it) }
        val recording = RecordingEngine { request ->
            if (request.url.encodedPath.endsWith("/posts")) {
                val offset = request.url.parameters["offset"]!!.toInt()
                json(encode(Page(slice(all, request), all.size.toLong(), page, offset)))
            } else {
                json(encode(CommunityProfile("Yorug' Tong", 1, memberSince = TestNow, postCount = all.size, posts = all.take(page))))
            }
        }
        val community = graph(recording).communityController(AppState())

        community.loadProfile("Yorug' Tong")
        assertEquals(page, community.profile?.posts?.size)
        assertTrue(community.profileHasMore)

        community.loadMoreProfilePosts()
        assertEquals(all.map { it.id }, community.profile?.posts?.map { it.id })
        assertFalse(community.profileHasMore)

        community.loadProfile("Yorug' Tong")
        assertEquals(all.size, community.profile?.posts?.size, "her bio saved, the posts she scrolled to stay")
        assertFalse(community.profileHasMore)
    }

    @Test
    fun `the notification history reads below the last row read, shown or not`() = runTest {
        val page = NotificationApi.HISTORY_PAGE
        // The newest page ends on a suppressed row: the cursor is that row, not the last one shown.
        val all = (0 until page + 2).map {
            NotificationMessage(
                id = "n$it",
                category = NotificationCategory.SYSTEM,
                title = "Xabar $it",
                body = "…",
                scheduledFor = TestNow - it.minutes,
                status = if (it == page - 1) NotificationStatus.SUPPRESSED else NotificationStatus.SENT,
                sentAt = TestNow - it.minutes,
            )
        }
        val befores = mutableListOf<String?>()
        val recording = RecordingEngine { request ->
            val before = request.url.parameters["before"].also(befores::add)
            val from = before?.let { cursor -> all.indexOfFirst { it.id == cursor } + 1 } ?: 0
            json(encode(all.drop(from).take(request.url.parameters["limit"]!!.toInt())))
        }
        val notifications = graph(recording).notificationsController()

        notifications.loadSent()
        assertEquals(page - 1, notifications.sent.size, "the suppressed one is not shown")
        assertTrue(notifications.sentHasMore)

        notifications.loadMoreSent()
        assertEquals("n${page - 1}", befores.last())
        assertEquals(page + 1, notifications.sent.size)
        assertFalse(notifications.sentHasMore)

        notifications.loadSent()
        assertEquals(page + 1, notifications.sent.size, "the refresh kept the older page")
        assertEquals(page + 2, notifications.historyLoaded)
    }
}
