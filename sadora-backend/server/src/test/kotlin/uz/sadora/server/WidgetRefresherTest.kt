package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import uz.sadora.server.notify.OutboxRecord
import uz.sadora.server.notify.PushSender
import uz.sadora.server.widget.WidgetRefresher

class WidgetRefresherTest {
    private class Recorder : PushSender {
        val silent = mutableListOf<List<String>>()
        override suspend fun send(record: OutboxRecord, pushTokens: List<String>) = true
        override suspend fun sendSilent(data: Map<String, String>, pushTokens: List<String>): Boolean {
            silent += pushTokens
            return true
        }
    }

    @Test
    fun `a burst of changes wakes her widgets once, after the burst`() = runTest {
        val sender = Recorder()
        val her = Uuid.random()
        val refresher = WidgetRefresher({ listOf("phone", "watch") }, sender, 10.seconds, backgroundScope)

        repeat(5) { refresher.changed(her) }
        runCurrent()
        assertEquals(0, sender.silent.size, "nothing goes out mid-burst")

        advanceTimeBy(11.seconds)
        assertEquals(listOf(listOf("phone", "watch")), sender.silent)

        refresher.changed(her)
        advanceTimeBy(11.seconds)
        assertEquals(2, sender.silent.size, "a later change is its own refresh")
    }

    @Test
    fun `an account with no devices sends nothing`() = runTest {
        val sender = Recorder()
        val refresher = WidgetRefresher({ emptyList() }, sender, 10.seconds, backgroundScope)
        refresher.changed(Uuid.random())
        advanceTimeBy(11.seconds)
        assertEquals(0, sender.silent.size)
    }
}
