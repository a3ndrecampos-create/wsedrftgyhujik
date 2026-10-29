package com.promoapp.data

import com.promoapp.data.local.toDomain
import com.promoapp.data.local.toEntity
import com.promoapp.data.remote.DealDto
import com.promoapp.data.remote.toDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class CachedDealMapperTest {
    private fun dto(tags: List<String>) =
        DealDto(
            id = 7,
            title = "Fone",
            imageUrl = "https://img",
            storeName = "Amazon",
            price = 99.9,
            originalPrice = 199.0,
            authorName = "Ana",
            postedAtMillis = 1_000,
            temperature = 42,
            commentCount = 3,
            tags = tags,
            url = "https://loja",
        )

    @Test
    fun `entity round trip keeps all fields`() {
        val dto = dto(listOf("Frete Grátis", "Cupom"))

        val result = dto.toEntity(tab = "FOR_YOU", position = 0, cachedAtMillis = 5).toDomain()

        assertEquals(dto.toDomain(), result)
    }

    @Test
    fun `empty tags survive the round trip`() {
        val dto = dto(emptyList())

        val result = dto.toEntity(tab = "RECENT", position = 3, cachedAtMillis = 5).toDomain()

        assertEquals(emptyList<String>(), result.tags)
    }

    @Test
    fun `entity keeps tab and position for ordering`() {
        val entity = dto(emptyList()).toEntity(tab = "TRENDING", position = 12, cachedAtMillis = 99)

        assertEquals("TRENDING", entity.tab)
        assertEquals(12, entity.position)
        assertEquals(99L, entity.cachedAtMillis)
    }
}
