package com.promoserver

import com.promoserver.pipeline.OfferNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OfferNormalizerTest {

    private val normalizer = OfferNormalizer(minDiscountPercent = 10)

    @Test
    fun `keeps a real deal and cleans the text`() {
        val result = normalizer.normalize(offer(title = "  Fone   Bluetooth \n ANC ", price = 80.0, original = 100.0))

        assertNotNull(result)
        assertEquals("Fone Bluetooth ANC", result.title)
        assertEquals(20, result.discountPercent)
    }

    @Test
    fun `drops offers without a discount`() {
        assertNull(normalizer.normalize(offer(original = null)))
        assertNull(normalizer.normalize(offer(price = 100.0, original = 100.0)))
        assertNull(normalizer.normalize(offer(price = 120.0, original = 100.0)))
    }

    @Test
    fun `drops offers below the minimum discount`() {
        assertNull(normalizer.normalize(offer(price = 95.0, original = 100.0)))
        assertNotNull(normalizer.normalize(offer(price = 90.0, original = 100.0)))
    }

    @Test
    fun `drops invalid prices, titles and links`() {
        assertNull(normalizer.normalize(offer(price = 0.0)))
        assertNull(normalizer.normalize(offer(price = Double.NaN)))
        assertNull(normalizer.normalize(offer(title = "   ")))
        assertNull(normalizer.normalize(offer().copy(url = "javascript:alert(1)")))
        assertNull(normalizer.normalize(offer().copy(url = "ftp://loja.com/x")))
    }

    @Test
    fun `upgrades image links to https and limits the title`() {
        val result = normalizer.normalize(offer(title = "a".repeat(300)).copy(imageUrl = "http://img.com/a.jpg"))

        assertNotNull(result)
        assertEquals("https://img.com/a.jpg", result.imageUrl)
        assertEquals(140, result.title.length)
    }
}
