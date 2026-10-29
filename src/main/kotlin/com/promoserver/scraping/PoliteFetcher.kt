package com.promoserver.scraping

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class RobotsDisallowedException(url: String) : RuntimeException("O robots.txt do site não permite acessar $url")

class BlockedException(message: String) : RuntimeException(message)

/**
 * Baixa páginas públicas com boas maneiras:
 *  - respeita o robots.txt (e na dúvida, não acessa);
 *  - identifica-se com um User-Agent próprio, sem fingir ser navegador;
 *  - espera um intervalo entre acessos ao mesmo site;
 *  - ao receber 403, 429 ou 503, para de acessar aquele site por algumas horas (não tenta burlar bloqueio).
 */
class PoliteFetcher(
    private val client: HttpClient,
    private val userAgent: String,
    private val minDelayMs: Long = 3_000,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val robotsCache = ConcurrentHashMap<String, Pair<Long, RobotsRules>>()
    private val lastRequestAt = ConcurrentHashMap<String, Long>()
    private val blockedUntil = ConcurrentHashMap<String, Long>()
    private val mutex = Mutex()

    suspend fun fetchHtml(url: String): String {
        val uri = URI(url.trim())
        require((uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()) { "URL inválida: $url" }
        val host = uri.host.lowercase()
        val origin = "${uri.scheme}://${uri.authority}"

        blockedUntil[host]?.takeIf { it > now() }?.let {
            throw BlockedException("$host está em pausa depois de um bloqueio (volta a tentar em ${(it - now()) / 60_000} min)")
        }
        val pathAndQuery = (uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
        if (!robotsFor(origin).isAllowed(pathAndQuery)) throw RobotsDisallowedException(url)

        throttle(host)
        val response = client.get(url) {
            header(HttpHeaders.UserAgent, userAgent)
            header(HttpHeaders.AcceptLanguage, "pt-BR,pt;q=0.9")
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
        }
        val code = response.status.value
        if (code == 403 || code == 429 || code == 503) {
            blockedUntil[host] = now() + BLOCK_PAUSE_MS
            throw BlockedException("$host respondeu $code. Pausando os acessos a ele por 6 horas.")
        }
        check(response.status.isSuccess()) { "$host respondeu $code" }
        val bytes = response.bodyAsBytes()
        check(bytes.size <= MAX_BYTES) { "Página grande demais (${bytes.size} bytes)" }
        return bytes.toString(Charsets.UTF_8)
    }

    private suspend fun throttle(host: String) = mutex.withLock {
        val wait = (lastRequestAt[host] ?: 0L) + minDelayMs - now()
        if (wait > 0) sleep(wait)
        lastRequestAt[host] = now()
    }

    private suspend fun robotsFor(origin: String): RobotsRules {
        robotsCache[origin]?.takeIf { it.first > now() }?.let { return it.second }
        var ttl = ROBOTS_TTL_MS
        val rules = try {
            val response = client.get("$origin/robots.txt") { header(HttpHeaders.UserAgent, userAgent) }
            when {
                response.status.isSuccess() -> RobotsRules.parse(response.bodyAsText(), userAgent)
                response.status.value in 400..499 -> RobotsRules.ALLOW_ALL // sem robots.txt: tudo liberado
                else -> {
                    ttl = ROBOTS_RETRY_MS
                    RobotsRules.DISALLOW_ALL // servidor com problema: na dúvida, não acessa
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ttl = ROBOTS_RETRY_MS
            RobotsRules.DISALLOW_ALL
        }
        robotsCache[origin] = (now() + ttl) to rules
        return rules
    }

    private companion object {
        const val MAX_BYTES = 5_000_000
        const val BLOCK_PAUSE_MS = 6 * 60 * 60 * 1000L
        const val ROBOTS_TTL_MS = 12 * 60 * 60 * 1000L
        const val ROBOTS_RETRY_MS = 10 * 60 * 1000L
    }
}
