package com.promoserver.config

import org.slf4j.LoggerFactory

data class AwinFeedConfig(val store: String, val url: String)

data class ShopeeConfig(val appId: String, val appSecret: String, val keywords: List<String>, val pages: Int = 2)

data class AmazonConfig(
    val credentialId: String,
    val credentialSecret: String,
    val partnerTag: String,
    val keywords: List<String>,
    val marketplace: String = "www.amazon.com.br",
    val tokenUrl: String = "https://api.amazon.com/auth/o2/token",
)

data class MercadoLivreConfig(
    val clientId: String,
    val clientSecret: String,
    val itemIds: List<String>,
    val searchTerms: List<String>,
    val affiliateUrlTemplate: String = "{url}",
)

data class AppConfig(
    val port: Int = 8080,
    val dbPath: String = "promo.db",
    val adminToken: String? = null,
    val allowPublicPosts: Boolean = false,
    val collectIntervalHours: Long = 6,
    val collectOnStart: Boolean = true,
    val minDiscountPercent: Int = 10,
    val demoMode: Boolean = false,
    val awinFeeds: List<AwinFeedConfig> = emptyList(),
    val shopee: ShopeeConfig? = null,
    val amazon: AmazonConfig? = null,
    val mercadoLivre: MercadoLivreConfig? = null,
    val scrapeSites: List<ScrapeSiteConfig> = emptyList(),
    val scraperUserAgent: String = "PromoAppBot/1.0",
    val scraperMinDelayMs: Long = 3_000,
    val publicBaseUrl: String? = null,
    val retentionDays: Int = 60,
    val rateLimitPerMinute: Int = 120,
    val trustProxy: Boolean = false,
) {
    companion object {
        const val STORE_ML = "Mercado Livre"
        const val STORE_AMAZON = "Amazon"
        const val STORE_SHOPEE = "Shopee"
        const val STORE_NATURA = "Natura"
        const val STORE_BOTICARIO = "O Boticário"
        val ALL_STORES = listOf(STORE_ML, STORE_AMAZON, STORE_SHOPEE, STORE_NATURA, STORE_BOTICARIO)

        const val MIN_ADMIN_TOKEN_LENGTH = 16
        private const val PLACEHOLDER_TOKEN = "troque-por-um-segredo-longo"
        private val log = LoggerFactory.getLogger(AppConfig::class.java)

        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            fun value(key: String) = env[key]?.trim()?.takeIf { it.isNotEmpty() }
            fun list(key: String) = value(key)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

            val awin = buildList {
                value("AWIN_FEED_NATURA_URL")?.let { add(AwinFeedConfig(STORE_NATURA, it)) }
                value("AWIN_FEED_BOTICARIO_URL")?.let { add(AwinFeedConfig(STORE_BOTICARIO, it)) }
            }
            val shopee = value("SHOPEE_APP_ID")?.let { id ->
                value("SHOPEE_APP_SECRET")?.let { ShopeeConfig(id, it, list("SHOPEE_KEYWORDS")) }
            }
            val amazon = value("AMAZON_CREDENTIAL_ID")?.let { id ->
                val secret = value("AMAZON_CREDENTIAL_SECRET")
                val tag = value("AMAZON_PARTNER_TAG")
                if (secret != null && tag != null) {
                    AmazonConfig(
                        credentialId = id,
                        credentialSecret = secret,
                        partnerTag = tag,
                        keywords = list("AMAZON_KEYWORDS").ifEmpty { listOf("oferta") },
                        marketplace = value("AMAZON_MARKETPLACE") ?: "www.amazon.com.br",
                        tokenUrl = value("AMAZON_TOKEN_URL") ?: "https://api.amazon.com/auth/o2/token",
                    )
                } else {
                    null
                }
            }
            val ml = value("ML_CLIENT_ID")?.let { id ->
                value("ML_CLIENT_SECRET")?.let {
                    MercadoLivreConfig(
                        clientId = id,
                        clientSecret = it,
                        itemIds = list("ML_ITEM_IDS"),
                        searchTerms = list("ML_SEARCH_TERMS"),
                        affiliateUrlTemplate = value("ML_AFFILIATE_URL_TEMPLATE") ?: "{url}",
                    )
                }
            }
            val sites = ScrapeSites.load(value("SCRAPE_SITES_FILE") ?: "sites.json")
            val contact = value("SCRAPER_CONTACT")
            val userAgent = value("SCRAPER_USER_AGENT") ?: listOfNotNull("PromoAppBot/1.0", contact?.let { "(+$it)" }).joinToString(" ")
            val hasRealCollector = awin.isNotEmpty() || shopee != null || amazon != null || ml != null || sites.isNotEmpty()
            return AppConfig(
                port = value("PORT")?.toIntOrNull() ?: 8080,
                dbPath = value("DB_PATH") ?: "promo.db",
                adminToken = validAdminToken(value("ADMIN_TOKEN")),
                allowPublicPosts = value("ALLOW_PUBLIC_POSTS")?.toBooleanStrictOrNull() ?: false,
                collectIntervalHours = value("COLLECT_INTERVAL_HOURS")?.toLongOrNull()?.coerceAtLeast(1) ?: 6,
                collectOnStart = value("COLLECT_ON_START")?.toBooleanStrictOrNull() ?: true,
                minDiscountPercent = value("MIN_DISCOUNT_PERCENT")?.toIntOrNull()?.coerceIn(0, 90) ?: 10,
                demoMode = value("DEMO_MODE")?.toBooleanStrictOrNull() ?: !hasRealCollector,
                awinFeeds = awin,
                shopee = shopee,
                amazon = amazon,
                mercadoLivre = ml,
                scrapeSites = sites,
                scraperUserAgent = userAgent,
                scraperMinDelayMs = value("SCRAPER_MIN_DELAY_MS")?.toLongOrNull()?.coerceAtLeast(1_000) ?: 3_000,
                publicBaseUrl = value("PUBLIC_BASE_URL")?.trimEnd('/'),
                retentionDays = value("RETENTION_DAYS")?.toIntOrNull()?.coerceAtLeast(1) ?: 60,
                rateLimitPerMinute = value("RATE_LIMIT_PER_MINUTE")?.toIntOrNull()?.coerceAtLeast(0) ?: 120,
                trustProxy = value("TRUST_PROXY")?.toBooleanStrictOrNull() ?: false,
            )
        }

        /** Token curto ou de exemplo deixaria o painel aberto para qualquer um: nesse caso o admin fica desligado. */
        private fun validAdminToken(token: String?): String? {
            if (token == null) return null
            if (token.length < MIN_ADMIN_TOKEN_LENGTH || token == PLACEHOLDER_TOKEN) {
                log.warn("ADMIN_TOKEN ausente, curto (mínimo {} caracteres) ou de exemplo: o painel de administração ficará desligado.", MIN_ADMIN_TOKEN_LENGTH)
                return null
            }
            return token
        }
    }
}
