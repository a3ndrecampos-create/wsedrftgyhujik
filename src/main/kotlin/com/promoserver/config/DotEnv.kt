package com.promoserver.config

import java.io.File

/**
 * Lê variáveis de um arquivo `promo.env` (formato CHAVE=valor). Hospedagens que só aceitam enviar arquivos
 * (como a ShardCloud) usam isso no lugar de variáveis de ambiente. Variáveis de ambiente reais têm prioridade.
 */
object DotEnv {
    private val validKey = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun parse(text: String): Map<String, String> = text.removePrefix("\uFEFF").lineSequence().mapNotNull { raw ->
        val line = raw.trim().removePrefix("export ").trim()
        if (line.isEmpty() || line.startsWith("#") || !line.contains('=')) return@mapNotNull null
        val key = line.substringBefore('=').trim()
        if (!validKey.matches(key)) return@mapNotNull null
        val rawValue = line.substringAfter('=').trim()
        val quoted = rawValue.length >= 2 &&
            ((rawValue.startsWith('"') && rawValue.endsWith('"')) || (rawValue.startsWith('\'') && rawValue.endsWith('\'')))
        val value = if (quoted) rawValue.substring(1, rawValue.length - 1) else rawValue.substringBefore(" #").trim()
        key to value
    }.toMap()

    fun load(path: String): Map<String, String> = File(path).takeIf { it.isFile }?.let { parse(it.readText()) }.orEmpty()

    /** Variáveis do arquivo (ENV_FILE ou promo.env) sobrescritas pelas do ambiente real. */
    fun merged(env: Map<String, String> = System.getenv(), defaultFile: String = "promo.env"): Map<String, String> =
        load(env["ENV_FILE"] ?: defaultFile) + env
}
