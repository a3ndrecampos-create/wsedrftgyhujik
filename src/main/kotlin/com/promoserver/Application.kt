package com.promoserver

import com.promoserver.collectors.CollectorFactory
import com.promoserver.config.AppConfig
import com.promoserver.config.DotEnv
import com.promoserver.db.AppDatabase
import com.promoserver.db.CouponRepository
import com.promoserver.db.OfferRepository
import com.promoserver.db.RunRepository
import com.promoserver.model.ErrorDto
import com.promoserver.pipeline.CollectionScheduler
import com.promoserver.pipeline.CollectionService
import com.promoserver.pipeline.OfferNormalizer
import com.promoserver.pipeline.RateLimiter
import com.promoserver.pipeline.RetentionService
import com.promoserver.routes.apiRoutes
import com.promoserver.scraping.PoliteFetcher
import com.promoserver.scraping.ScrapeEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

val promoJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

class AppDependencies(
    val config: AppConfig,
    val offers: OfferRepository,
    val coupons: CouponRepository,
    val collection: CollectionService,
    val scheduler: CollectionScheduler?,
    val scrapeEngine: ScrapeEngine? = null,
    val runs: RunRepository = RunRepository(),
    val rateLimiter: RateLimiter? = null,
) {
    companion object {
        fun create(config: AppConfig): AppDependencies {
            AppDatabase.connect(config.dbPath)
            val offers = OfferRepository()
            val client = HttpClient(CIO) {
                install(HttpTimeout) { requestTimeoutMillis = 60_000 }
                expectSuccess = false
            }
            val engine = ScrapeEngine(PoliteFetcher(client, config.scraperUserAgent, config.scraperMinDelayMs))
            val collectors = CollectorFactory.create(config, client, engine)
            val runs = RunRepository()
            val service = CollectionService(collectors, OfferNormalizer(config.minDiscountPercent), offers, runs)
            val retention = RetentionService(offers, runs, config.retentionDays)
            return AppDependencies(
                config = config,
                offers = offers,
                coupons = CouponRepository(),
                collection = service,
                scheduler = CollectionScheduler(service, config.collectIntervalHours, config.collectOnStart) { retention.cleanup() },
                scrapeEngine = engine,
                runs = runs,
                rateLimiter = config.rateLimitPerMinute.takeIf { it > 0 }?.let { RateLimiter(it) },
            )
        }
    }
}

fun main() {
    val config = AppConfig.fromEnv(DotEnv.merged())
    val deps = AppDependencies.create(config)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { promoModule(deps) }.start(wait = true)
}

fun Application.promoModule(deps: AppDependencies) {
    install(ContentNegotiation) { json(promoJson) }
    install(CallLogging)
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorDto("Requisição inválida"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Erro não tratado", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("Erro interno"))
        }
    }
    deps.rateLimiter?.let { limiter ->
        intercept(ApplicationCallPipeline.Plugins) {
            val path = call.request.path()
            if (path.startsWith("/admin") || path == "/health") return@intercept
            val retryAfter = limiter.check(clientKey(call, deps.config.trustProxy))
            if (retryAfter != null) {
                call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
                call.respond(HttpStatusCode.TooManyRequests, ErrorDto("Muitas requisições. Tente de novo em instantes."))
                finish()
            }
        }
    }
    routing { apiRoutes(deps) }
    deps.scheduler?.start(this)
}

/** Atrás de um proxy (Caddy, Nginx), o IP real vem em X-Forwarded-For. Só confie nele com TRUST_PROXY=true. */
private fun clientKey(call: ApplicationCall, trustProxy: Boolean): String {
    val forwarded = if (trustProxy) call.request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim() else null
    return forwarded?.takeIf { it.isNotEmpty() } ?: call.request.origin.remoteAddress
}
