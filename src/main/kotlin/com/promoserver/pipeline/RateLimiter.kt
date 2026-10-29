package com.promoserver.pipeline

import java.util.concurrent.ConcurrentHashMap

/** Limite simples por chave (IP) em janelas de 1 minuto, na memória. Suficiente para um servidor único. */
class RateLimiter(
    private val perMinute: Int,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Window(val minute: Long, val count: Int)

    private val windows = ConcurrentHashMap<String, Window>()

    /** Devolve null se a chamada é permitida, ou quantos segundos faltam para liberar. */
    fun check(key: String): Int? {
        val millis = now()
        val minute = millis / MINUTE_MS
        var retryAfter: Int? = null
        windows.compute(key) { _, window ->
            val current = if (window == null || window.minute != minute) Window(minute, 0) else window
            if (current.count >= perMinute) {
                retryAfter = (SECONDS_PER_MINUTE - (millis / 1000) % SECONDS_PER_MINUTE).toInt()
                current
            } else {
                Window(minute, current.count + 1)
            }
        }
        if (windows.size > MAX_KEYS) windows.entries.removeIf { it.value.minute != minute }
        return retryAfter
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val SECONDS_PER_MINUTE = 60L
        const val MAX_KEYS = 10_000
    }
}
