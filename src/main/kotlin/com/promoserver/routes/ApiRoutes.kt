package com.promoserver.routes

import com.promoserver.AppDependencies
import com.promoserver.db.AppDatabase
import com.promoserver.db.OfferRow
import com.promoserver.db.SearchQuery
import com.promoserver.model.CouponDto
import com.promoserver.model.DealDto
import com.promoserver.model.ErrorDto
import com.promoserver.model.FilterOptionsDto
import com.promoserver.model.NewCouponRequest
import com.promoserver.model.NewDealRequest
import com.promoserver.model.PreviewProduct
import com.promoserver.model.PreviewRequest
import com.promoserver.model.PreviewResponse
import com.promoserver.model.StatusDto
import com.promoserver.model.StoreStatDto
import com.promoserver.scraping.BlockedException
import com.promoserver.scraping.RobotsDisallowedException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

private const val DEFAULT_PAGE_SIZE = 20
private const val MAX_PAGE_SIZE = 100
private const val PREVIEW_SAMPLE = 5
private const val DAY_MS = 86_400_000L

/** Com PUBLIC_BASE_URL, o link do app passa pelo servidor (/go/{id}): conta cliques e permite trocar o destino sem atualizar o app. */
fun OfferRow.toDto(publicBaseUrl: String? = null) = DealDto(
    id = id,
    title = title,
    imageUrl = imageUrl,
    store = store,
    price = price,
    originalPrice = originalPrice,
    author = author ?: "PromoApp",
    postedAt = postedAt,
    temperature = discountPercent,
    comments = 0,
    tags = tags,
    url = if (publicBaseUrl != null) "$publicBaseUrl/go/$id" else url,
)

fun Route.apiRoutes(deps: AppDependencies) {
    val offers = deps.offers
    val config = deps.config

    get("/health") { call.respond(mapOf("status" to "ok")) }

    // Redireciona para a loja e conta o clique.
    get("/go/{id}") {
        val target = call.parameters["id"]?.toLongOrNull()?.let { id -> io { offers.recordClick(id, System.currentTimeMillis()) } }
        if (target == null) {
            call.respond(HttpStatusCode.NotFound, ErrorDto("Oferta não encontrada"))
        } else {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondRedirect(target, permanent = false)
        }
    }

    // Painel de administração (a página é estática; os dados exigem o token).
    get("/admin") {
        call.response.header(HttpHeaders.ContentSecurityPolicy, "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'")
        call.response.header(HttpHeaders.XFrameOptions, "DENY")
        call.respondText(AdminPage.html, ContentType.Text.Html)
    }

    // Baixa uma cópia do banco. Guarde de tempos em tempos, principalmente se o disco da hospedagem não for permanente.
    get("/admin/backup") {
        if (!call.isAdmin(config.adminToken)) return@get
        val bytes = io { AppDatabase.backup() }
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"promo-backup.db\"")
        call.respondBytes(bytes, ContentType.Application.OctetStream)
    }

    get("/admin/status") {
        if (!call.isAdmin(config.adminToken)) return@get
        val now = System.currentTimeMillis()
        val status = io {
            val counts = offers.counts()
            StatusDto(
                demoMode = config.demoMode,
                publicBaseUrl = config.publicBaseUrl,
                collectors = deps.collection.collectorNames,
                activeOffers = counts.active,
                inactiveOffers = counts.inactive,
                userPosts = counts.userPosts,
                clicks24h = offers.clicksSince(now - DAY_MS),
                stores = offers.storeStats().map { StoreStatDto(it.store, it.activeOffers, it.lastSeenAt) },
                lastRuns = deps.runs.latestPerCollector(),
            )
        }
        call.respond(status)
    }

    get("/deals") {
        val (page, size) = call.paging()
        val tab = call.request.queryParameters["tab"] ?: "for_you"
        val rows = io { offers.list(tab, page, size, System.currentTimeMillis()) }
        call.respond(rows.map { it.toDto(config.publicBaseUrl) })
    }

    get("/deals/search") {
        val (page, size) = call.paging()
        val params = call.request.queryParameters
        val query = SearchQuery(
            text = params["q"],
            stores = params["stores"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty(),
            category = params["category"]?.takeIf { it.isNotBlank() },
            minPrice = params["min_price"]?.toDoubleOrNull(),
            maxPrice = params["max_price"]?.toDoubleOrNull(),
            sort = params["sort"] ?: "relevance",
        )
        call.respond(io { offers.search(query, page, size) }.map { it.toDto(config.publicBaseUrl) })
    }

    get("/deals/mine") {
        call.respond(io { offers.myPosts() }.map { it.toDto(config.publicBaseUrl) })
    }

    get("/deals/{id}") {
        val row = call.parameters["id"]?.toLongOrNull()?.let { id -> io { offers.findById(id) } }
        if (row == null) call.respond(HttpStatusCode.NotFound, ErrorDto("Oferta não encontrada")) else call.respond(row.toDto(config.publicBaseUrl))
    }

    get("/deals/{id}/price-history") {
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) call.respond(HttpStatusCode.NotFound, ErrorDto("Oferta não encontrada")) else call.respond(io { offers.priceHistory(id) })
    }

    get("/filters") {
        val (stores, categories) = io { offers.filterOptions() }
        call.respond(FilterOptionsDto(stores, categories))
    }

    post("/deals") {
        if (!config.allowPublicPosts) {
            call.respond(HttpStatusCode.Forbidden, ErrorDto("Publicação de ofertas desativada neste servidor"))
            return@post
        }
        val request = call.receive<NewDealRequest>()
        val errors = validate(request)
        if (errors.isNotEmpty()) {
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorDto("Dados inválidos", errors))
            return@post
        }
        val row = io { offers.createUserPost(request, System.currentTimeMillis()) }
        call.respond(HttpStatusCode.Created, row.toDto(config.publicBaseUrl))
    }

    get("/coupons") {
        call.respond(io { deps.coupons.list() })
    }

    // --- Administração (exige o cabeçalho X-Admin-Token igual a ADMIN_TOKEN) ---

    post("/admin/collect") {
        if (!call.isAdmin(config.adminToken)) return@post
        val only = call.request.queryParameters["store"]
        call.respond(deps.collection.runAll(only))
    }

    // Mostra o que o robô enxergaria em uma página, sem salvar nada. Use para ajustar sites.json.
    post("/admin/scrape-preview") {
        if (!call.isAdmin(config.adminToken)) return@post
        val engine = deps.scrapeEngine
        if (engine == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorDto("Leitura de páginas indisponível"))
            return@post
        }
        val request = call.receive<PreviewRequest>()
        try {
            val result = engine.scrape(request.url, request.strategy, request.css)
            val sample = result.products.take(PREVIEW_SAMPLE).map {
                val discount = it.oldPrice?.takeIf { old -> old > it.price }?.let { old -> (((old - it.price) / old) * 100).toInt() } ?: 0
                PreviewProduct(it.title, it.price, it.oldPrice, discount, it.url, it.imageUrl)
            }
            call.respond(
                PreviewResponse(
                    strategy = result.strategy,
                    found = result.products.size,
                    withDiscount = result.products.count { it.oldPrice != null },
                    sample = sample,
                    warnings = result.warnings,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: RobotsDisallowedException) {
            call.respond(HttpStatusCode.Forbidden, ErrorDto(e.message ?: "robots.txt não permite"))
        } catch (e: BlockedException) {
            call.respond(HttpStatusCode.BadGateway, ErrorDto(e.message ?: "Acesso bloqueado pelo site"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadGateway, ErrorDto(e.message ?: "Falha ao ler a página"))
        }
    }

    post("/admin/coupons") {
        if (!call.isAdmin(config.adminToken)) return@post
        val request = call.receive<NewCouponRequest>()
        if (request.code.isBlank() || request.store.isBlank() || !isHttpUrl(request.url)) {
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorDto("Cupom inválido"))
            return@post
        }
        val id = io { deps.coupons.create(request) }
        call.respond(
            HttpStatusCode.Created,
            CouponDto(id, request.code, request.store, request.description, request.discountLabel, request.minPurchase, request.expiresAtMillis, request.url),
        )
    }
}

internal object AdminPage {
    val html: String by lazy {
        checkNotNull(AdminPage::class.java.getResourceAsStream("/admin.html")) { "admin.html ausente" }
            .bufferedReader().use { it.readText() }
    }
}

private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

private fun ApplicationCall.paging(): Pair<Int, Int> {
    val page = request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val size = request.queryParameters["page_size"]?.toIntOrNull()?.coerceIn(1, MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
    return page to size
}

private suspend fun ApplicationCall.isAdmin(adminToken: String?): Boolean {
    val ok = adminToken != null && request.headers["X-Admin-Token"] == adminToken
    if (!ok) {
        val status = if (adminToken == null) HttpStatusCode.Forbidden else HttpStatusCode.Unauthorized
        respond(status, ErrorDto("Acesso restrito"))
    }
    return ok
}

internal fun validate(request: NewDealRequest): List<String> = buildList {
    if (request.title.trim().length !in 8..120) add("title")
    if (!isHttpUrl(request.url)) add("url")
    if (!request.imageUrl.isNullOrBlank() && !isHttpUrl(request.imageUrl)) add("image_url")
    if (request.store.isBlank()) add("store")
    if (request.price <= 0) add("price")
    if (request.originalPrice != null && request.originalPrice <= request.price) add("original_price")
    if (request.author.isBlank()) add("author")
}

private fun isHttpUrl(value: String): Boolean = runCatching {
    val uri = URI(value.trim())
    (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)
