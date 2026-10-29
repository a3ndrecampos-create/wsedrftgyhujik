package com.promoserver

import com.promoserver.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppConfigTest {

    @Test
    fun `without credentials the server starts in demo mode with safe defaults`() {
        val config = AppConfig.fromEnv(emptyMap())

        assertTrue(config.demoMode)
        assertFalse(config.allowPublicPosts)
        assertNull(config.adminToken)
        assertEquals(8080, config.port)
        assertEquals(6, config.collectIntervalHours)
    }

    @Test
    fun `reads credentials for each store`() {
        val config = AppConfig.fromEnv(
            mapOf(
                "AWIN_FEED_NATURA_URL" to "https://feed/natura",
                "AWIN_FEED_BOTICARIO_URL" to "https://feed/boti",
                "SHOPEE_APP_ID" to "1",
                "SHOPEE_APP_SECRET" to "s",
                "SHOPEE_KEYWORDS" to "perfume, fone ",
                "AMAZON_CREDENTIAL_ID" to "id",
                "AMAZON_CREDENTIAL_SECRET" to "secret",
                "AMAZON_PARTNER_TAG" to "tag-20",
                "ML_CLIENT_ID" to "c",
                "ML_CLIENT_SECRET" to "cs",
                "ML_ITEM_IDS" to "MLB1,MLB2",
                "ADMIN_TOKEN" to "segredo-bem-longo-123456",
                "MIN_DISCOUNT_PERCENT" to "15",
            ),
        )

        assertFalse(config.demoMode)
        assertEquals(listOf("Natura", "O Boticário"), config.awinFeeds.map { it.store })
        assertEquals(listOf("perfume", "fone"), config.shopee!!.keywords)
        assertEquals(listOf("oferta"), config.amazon!!.keywords)
        assertEquals("www.amazon.com.br", config.amazon!!.marketplace)
        assertEquals(listOf("MLB1", "MLB2"), config.mercadoLivre!!.itemIds)
        assertEquals("{url}", config.mercadoLivre!!.affiliateUrlTemplate)
        assertEquals(15, config.minDiscountPercent)
        assertNotNull(config.adminToken)
    }

    @Test
    fun `incomplete credentials are ignored`() {
        val config = AppConfig.fromEnv(mapOf("SHOPEE_APP_ID" to "1", "AMAZON_CREDENTIAL_ID" to "x"))

        assertNull(config.shopee)
        assertNull(config.amazon)
        assertTrue(config.demoMode)
    }

    @Test
    fun `weak or example admin tokens turn the admin off`() {
        assertNull(AppConfig.fromEnv(mapOf("ADMIN_TOKEN" to "curto")).adminToken)
        assertNull(AppConfig.fromEnv(mapOf("ADMIN_TOKEN" to "troque-por-um-segredo-longo")).adminToken)
        assertNull(AppConfig.fromEnv(mapOf("ADMIN_TOKEN" to "")).adminToken)
        assertEquals("0123456789abcdef", AppConfig.fromEnv(mapOf("ADMIN_TOKEN" to "0123456789abcdef")).adminToken)
    }

    @Test
    fun `reads the new options with safe defaults`() {
        val config = AppConfig.fromEnv(mapOf("PUBLIC_BASE_URL" to "https://x.shardweb.app/", "TRUST_PROXY" to "true", "RETENTION_DAYS" to "30"))

        assertEquals("https://x.shardweb.app", config.publicBaseUrl)
        assertTrue(config.trustProxy)
        assertEquals(30, config.retentionDays)
        assertEquals(120, config.rateLimitPerMinute)
    }
}
