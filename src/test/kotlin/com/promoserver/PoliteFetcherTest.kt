package com.promoserver

import com.promoserver.scraping.BlockedException
import com.promoserver.scraping.PoliteFetcher
import com.promoserver.scraping.RobotsDisallowedException
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PoliteFetcherTest {

    private class Setup(robots: String?, robotsStatus: HttpStatusCode, pageStatus: HttpStatusCode) {
        val requests = mutableListOf<String>()
        val userAgents = mutableListOf<String?>()
        val sleeps = mutableListOf<Long>()
        val fetcher = PoliteFetcher(
            client = mockClient { request ->
                requests += request.url.encodedPath
                userAgents += request.headers["User-Agent"]
                if (request.url.encodedPath == "/robots.txt") {
                    respond(robots.orEmpty(), robotsStatus)
                } else {
                    respond("<html>ok</html>", pageStatus)
                }
            },
            userAgent = "PromoAppBot/1.0",
            minDelayMs = 3_000,
            now = { 10_000_000L },
            sleep = { sleeps += it },
        )
    }

    private fun setup(
        robots: String? = "User-agent: *\nDisallow: /privado",
        robotsStatus: HttpStatusCode = HttpStatusCode.OK,
        pageStatus: HttpStatusCode = HttpStatusCode.OK,
    ) = Setup(robots, robotsStatus, pageStatus)

    @Test
    fun `fetches an allowed page identifying itself`() = runTest {
        val s = setup()

        assertEquals("<html>ok</html>", s.fetcher.fetchHtml("https://loja.com/ofertas"))
        assertTrue(s.userAgents.all { it == "PromoAppBot/1.0" })
    }

    @Test
    fun `does not request a page that robots txt disallows`() = runTest {
        val s = setup()

        assertFailsWith<RobotsDisallowedException> { s.fetcher.fetchHtml("https://loja.com/privado/x") }
        assertEquals(listOf("/robots.txt"), s.requests)
    }

    @Test
    fun `missing robots txt means everything is allowed`() = runTest {
        val s = setup(robots = null, robotsStatus = HttpStatusCode.NotFound)

        assertEquals("<html>ok</html>", s.fetcher.fetchHtml("https://loja.com/privado/x"))
    }

    @Test
    fun `a server error on robots txt means do not fetch`() = runTest {
        val s = setup(robotsStatus = HttpStatusCode.InternalServerError)

        assertFailsWith<RobotsDisallowedException> { s.fetcher.fetchHtml("https://loja.com/ofertas") }
    }

    @Test
    fun `a 429 pauses the site and later calls fail without a new request`() = runTest {
        val s = setup(pageStatus = HttpStatusCode.TooManyRequests)

        assertFailsWith<BlockedException> { s.fetcher.fetchHtml("https://loja.com/ofertas") }
        assertFailsWith<BlockedException> { s.fetcher.fetchHtml("https://loja.com/outra") }

        assertEquals(1, s.requests.count { it == "/ofertas" })
        assertEquals(0, s.requests.count { it == "/outra" })
    }

    @Test
    fun `403 and 503 are treated as blocks too`() = runTest {
        assertFailsWith<BlockedException> { setup(pageStatus = HttpStatusCode.Forbidden).fetcher.fetchHtml("https://a.com/x") }
        assertFailsWith<BlockedException> { setup(pageStatus = HttpStatusCode.ServiceUnavailable).fetcher.fetchHtml("https://b.com/x") }
    }

    @Test
    fun `waits between requests to the same host`() = runTest {
        val s = setup()

        s.fetcher.fetchHtml("https://loja.com/a")
        s.fetcher.fetchHtml("https://loja.com/b")

        assertEquals(listOf(3_000L), s.sleeps)
    }

    @Test
    fun `rejects non http urls`() = runTest {
        assertFailsWith<IllegalArgumentException> { setup().fetcher.fetchHtml("ftp://loja.com/x") }
    }
}
