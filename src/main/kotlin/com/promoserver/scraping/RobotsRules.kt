package com.promoserver.scraping

/** Regras do robots.txt para o nosso robô. Segue a regra da correspondência mais longa; empate favorece "Allow". */
class RobotsRules private constructor(private val rules: List<Rule>) {

    private class Rule(val allow: Boolean, val regex: Regex, val length: Int)

    fun isAllowed(pathAndQuery: String): Boolean {
        val matching = rules.filter { it.regex.containsMatchIn(pathAndQuery) }
        val best = matching.maxWithOrNull(compareBy<Rule>({ it.length }, { it.allow })) ?: return true
        return best.allow
    }

    private class Group {
        val agents = mutableListOf<String>()
        val rules = mutableListOf<Pair<Boolean, String>>()
    }

    companion object {
        val ALLOW_ALL = RobotsRules(emptyList())
        val DISALLOW_ALL = RobotsRules(listOf(Rule(false, Regex("^/"), 1)))

        fun parse(text: String, userAgent: String): RobotsRules {
            val groups = mutableListOf<Group>()
            var current: Group? = null
            var lastWasAgent = false
            text.lineSequence().forEach { line ->
                val clean = line.substringBefore('#').trim()
                if (clean.isEmpty() || !clean.contains(':')) return@forEach
                val key = clean.substringBefore(':').trim().lowercase()
                val value = clean.substringAfter(':').trim()
                when (key) {
                    "user-agent" -> {
                        val group = current?.takeIf { lastWasAgent } ?: Group().also {
                            groups.add(it)
                            current = it
                        }
                        group.agents.add(value.lowercase())
                        lastWasAgent = true
                    }
                    "allow", "disallow" -> {
                        current?.rules?.add((key == "allow") to value)
                        lastWasAgent = false
                    }
                    else -> lastWasAgent = false
                }
            }
            val token = userAgent.substringBefore('/').trim().lowercase()
            val specific = groups.filter { group -> group.agents.any { it != "*" && it.isNotEmpty() && token.contains(it) } }
            val chosen = specific.ifEmpty { groups.filter { "*" in it.agents } }
            val rules = chosen.flatMap { it.rules }
                .filter { it.second.isNotEmpty() }
                .map { (allow, pattern) -> Rule(allow, toRegex(pattern), pattern.length) }
            return RobotsRules(rules)
        }

        private fun toRegex(pattern: String): Regex {
            val anchored = pattern.endsWith("$")
            val body = if (anchored) pattern.dropLast(1) else pattern
            val escaped = body.split("*").joinToString(".*") { Regex.escape(it) }
            return Regex("^" + escaped + if (anchored) "$" else "")
        }
    }
}
