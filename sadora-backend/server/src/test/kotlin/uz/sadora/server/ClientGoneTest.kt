package uz.sadora.server

import io.ktor.util.cio.ChannelReadException
import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.ClosedByteChannelException
import io.ktor.utils.io.ClosedWriteChannelException
import java.io.IOException
import java.nio.channels.ClosedChannelException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uz.sadora.server.plugins.isClientGone

class ClientGoneTest {
    @Test
    fun `a player that dropped a range mid-stream is a client gone`() {
        // The chain Netty gave when a video player abandoned one range for the next.
        val dropped = ClosedByteChannelException(ChannelWriteException(exception = ClosedChannelException()))
        assertTrue(dropped.isClientGone())
    }

    @Test
    fun `a download cut off while the file is copied out is a client gone`() {
        // The other shape: the engine had already closed the answer when the next piece came.
        assertTrue(ClosedWriteChannelException().isClientGone())
    }

    @Test
    fun `an upload cut off mid-body is a client gone`() {
        assertTrue(ChannelReadException(exception = IOException("Connection reset by peer")).isClientGone())
    }

    @Test
    fun `the server's own failures are not`() {
        assertFalse(IllegalStateException("no such key").isClientGone())
        // A media file that cannot be read is a fault of ours, though it is an IOException too.
        assertFalse(IOException("Input/output error").isClientGone())
        assertFalse(RuntimeException(IOException("disk")).isClientGone())
    }
}
