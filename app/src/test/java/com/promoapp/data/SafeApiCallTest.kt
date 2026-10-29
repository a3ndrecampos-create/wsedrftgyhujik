package com.promoapp.data

import com.promoapp.data.remote.safeApiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SafeApiCallTest {
    @Test
    fun `success is wrapped in Result`() =
        runTest {
            assertEquals(42, safeApiCall { 42 }.getOrNull())
        }

    @Test
    fun `io failure becomes a failed Result`() =
        runTest {
            val result = safeApiCall<Int> { throw IOException("sem rede") }

            assertTrue(result.isFailure)
        }

    @Test
    fun `cancellation is rethrown, never swallowed`() =
        runTest {
            try {
                safeApiCall<Int> { throw CancellationException("cancelado") }
                fail("A cancelação deveria ser propagada")
            } catch (expected: CancellationException) {
                assertEquals("cancelado", expected.message)
            }
        }
}
