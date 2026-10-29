package com.promoserver

import com.promoserver.scraping.RobotsRules
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobotsRulesTest {

    private val agent = "PromoAppBot/1.0 (+https://exemplo.com/bot)"

    @Test
    fun `disallow blocks a path prefix and the rest stays allowed`() {
        val rules = RobotsRules.parse("User-agent: *\nDisallow: /carrinho\nDisallow: /busca?", agent)

        assertFalse(rules.isAllowed("/carrinho/itens"))
        assertFalse(rules.isAllowed("/busca?q=fone"))
        assertTrue(rules.isAllowed("/ofertas"))
    }

    @Test
    fun `empty disallow allows everything`() {
        assertTrue(RobotsRules.parse("User-agent: *\nDisallow:", agent).isAllowed("/qualquer/coisa"))
    }

    @Test
    fun `longest match wins and allow wins ties`() {
        val rules = RobotsRules.parse("User-agent: *\nDisallow: /ofertas\nAllow: /ofertas/hoje\nDisallow: /x\nAllow: /x", agent)

        assertFalse(rules.isAllowed("/ofertas/amanha"))
        assertTrue(rules.isAllowed("/ofertas/hoje"))
        assertTrue(rules.isAllowed("/x"))
    }

    @Test
    fun `wildcards and end anchors work`() {
        val rules = RobotsRules.parse("User-agent: *\nDisallow: /*.pdf$\nDisallow: /*?sessao=", agent)

        assertFalse(rules.isAllowed("/docs/catalogo.pdf"))
        assertTrue(rules.isAllowed("/docs/catalogo.pdf.html"))
        assertFalse(rules.isAllowed("/produto?sessao=1"))
    }

    @Test
    fun `a group that names our robot overrides the generic one`() {
        val robots = "User-agent: *\nAllow: /\n\nUser-agent: PromoAppBot\nDisallow: /\n"

        assertFalse(RobotsRules.parse(robots, agent).isAllowed("/ofertas"))
        assertTrue(RobotsRules.parse(robots, "OutroRobo/2.0").isAllowed("/ofertas"))
    }

    @Test
    fun `several user agents can share one group and comments are ignored`() {
        val robots = "User-agent: Googlebot # buscador\nUser-agent: PromoAppBot\nDisallow: /privado # só humanos\n"

        assertFalse(RobotsRules.parse(robots, agent).isAllowed("/privado"))
        assertTrue(RobotsRules.parse(robots, agent).isAllowed("/publico"))
    }

    @Test
    fun `constants allow or block everything`() {
        assertTrue(RobotsRules.ALLOW_ALL.isAllowed("/qualquer"))
        assertFalse(RobotsRules.DISALLOW_ALL.isAllowed("/qualquer"))
    }
}
