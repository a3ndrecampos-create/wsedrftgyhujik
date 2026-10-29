package com.promoapp.ui.search

/** Converte o texto digitado ("12,50" ou "12.5") em preço. Retorna null se vazio, inválido ou negativo. */
internal fun parsePrice(input: String): Double? =
    input
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it >= 0 }

/** Mostra "100" em vez de "100.0" ao preencher o campo com um valor já aplicado. */
internal fun formatPriceInput(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
