package com.kurogane.dividend.data

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class InputSafetyTest {
    @Test fun acceptsExactlyTheLimitAndEmptyFiles() {
        assertArrayEquals(byteArrayOf(1, 2), ByteArrayInputStream(byteArrayOf(1, 2)).readBoundedBytes(2))
        assertArrayEquals(byteArrayOf(), ByteArrayInputStream(byteArrayOf()).readBoundedBytes(1))
    }

    @Test fun rejectsOversizeBeforeReadingTheWholeStream() {
        val input = ByteArrayInputStream(ByteArray(100_000))
        assertThrows(IllegalArgumentException::class.java) { input.readBoundedBytes(10) }
        assertEquals(99_989, input.available())
    }

    @Test fun rethrowsCancellationAndVirtualMachineErrors() {
        assertThrows(CancellationException::class.java) { runCatchingCancellable<Unit> { throw CancellationException("cancel") } }
        assertThrows(OutOfMemoryError::class.java) { runCatchingCancellable<Unit> { throw OutOfMemoryError("test") } }
    }

    @Test fun exposesOrdinaryCommunicationErrorsAsFailures() {
        val error = IOException("offline")
        assertSame(error, runCatchingCancellable<Unit> { throw error }.exceptionOrNull())
    }

    @Test fun zeroLengthBulkReadsDoNotFreezeTheReader() {
        val input = object : java.io.InputStream() {
            var remaining = 3
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = if (remaining > 0) 0 else -1
            override fun read(): Int = if (remaining-- > 0) 42 else -1
        }
        assertArrayEquals(byteArrayOf(42, 42, 42), input.readBoundedBytes(3))
    }

    @Test fun maximumIntegerLimitDoesNotOverflowTheProbeLength() {
        assertArrayEquals(byteArrayOf(1), ByteArrayInputStream(byteArrayOf(1)).readBoundedBytes(Int.MAX_VALUE))
    }
}
