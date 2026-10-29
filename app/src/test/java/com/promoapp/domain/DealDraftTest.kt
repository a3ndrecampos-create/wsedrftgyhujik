package com.promoapp.domain

import com.promoapp.FakeDealRepository
import com.promoapp.domain.model.DealDraft
import com.promoapp.domain.model.DraftError
import com.promoapp.domain.model.InvalidDraftException
import com.promoapp.domain.usecase.PostDealUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DealDraftTest {
    private fun validDraft() =
        DealDraft(
            title = "Fone Bluetooth com cancelamento de ruído",
            url = "https://loja.com.br/produto/123",
            store = "Amazon",
            price = 199.9,
            originalPrice = 399.0,
            authorName = "Ana",
        )

    @Test
    fun `complete draft has no errors`() {
        assertTrue(validDraft().validate().isEmpty())
    }

    @Test
    fun `optional fields can be empty`() {
        assertTrue(validDraft().copy(originalPrice = null, imageUrl = "", couponCode = "").validate().isEmpty())
    }

    @Test
    fun `title length is limited on both sides`() {
        assertEquals(setOf(DraftError.TITLE_TOO_SHORT), validDraft().copy(title = "Fone").validate())
        assertEquals(setOf(DraftError.TITLE_TOO_LONG), validDraft().copy(title = "a".repeat(121)).validate())
        assertEquals(setOf(DraftError.TITLE_TOO_SHORT), validDraft().copy(title = "   Fone   ").validate())
    }

    @Test
    fun `only http and https links with a host are accepted`() {
        listOf("ftp://loja.com", "loja.com", "https://", "texto qualquer", "").forEach { url ->
            assertEquals(url, setOf(DraftError.INVALID_URL), validDraft().copy(url = url).validate())
        }
        assertTrue(validDraft().copy(url = "http://loja.com").validate().isEmpty())
    }

    @Test
    fun `image link is validated only when filled`() {
        assertEquals(setOf(DraftError.INVALID_IMAGE_URL), validDraft().copy(imageUrl = "foto.png").validate())
        assertTrue(validDraft().copy(imageUrl = "https://img.com/a.png").validate().isEmpty())
    }

    @Test
    fun `price must be positive`() {
        assertEquals(setOf(DraftError.INVALID_PRICE), validDraft().copy(price = null, originalPrice = null).validate())
        assertEquals(setOf(DraftError.INVALID_PRICE), validDraft().copy(price = 0.0, originalPrice = null).validate())
    }

    @Test
    fun `original price must be higher than the price`() {
        assertEquals(setOf(DraftError.ORIGINAL_NOT_HIGHER), validDraft().copy(originalPrice = 199.9).validate())
        assertEquals(setOf(DraftError.ORIGINAL_NOT_HIGHER), validDraft().copy(originalPrice = 100.0).validate())
    }

    @Test
    fun `store and author are required`() {
        assertEquals(
            setOf(DraftError.MISSING_STORE, DraftError.MISSING_AUTHOR),
            validDraft().copy(store = " ", authorName = "").validate(),
        )
    }

    @Test
    fun `use case rejects an invalid draft without calling the repository`() =
        runTest {
            val repository = FakeDealRepository()

            val result = PostDealUseCase(repository)(validDraft().copy(title = "Oi"))

            assertTrue(result.exceptionOrNull() is InvalidDraftException)
            assertTrue(repository.postedDrafts.isEmpty())
        }

    @Test
    fun `use case sends a valid draft`() =
        runTest {
            val repository = FakeDealRepository()

            val result = PostDealUseCase(repository)(validDraft())

            assertTrue(result.isSuccess)
            assertEquals(1, repository.postedDrafts.size)
        }
}
