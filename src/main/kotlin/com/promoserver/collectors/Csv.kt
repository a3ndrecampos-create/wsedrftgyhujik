package com.promoserver.collectors

/** Leitor de CSV (RFC 4180): aceita campos entre aspas, vírgulas e quebras de linha dentro das aspas. */
object Csv {
    fun parse(text: String, delimiter: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        fun endField() {
            row.add(field.toString())
            field.clear()
        }
        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == delimiter -> endField()
                !inQuotes && c == '\n' -> endRow()
                !inQuotes && c == '\r' -> Unit
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** Descobre o separador olhando a primeira linha (feeds da Awin podem usar vírgula ou ponto e vírgula). */
    fun detectDelimiter(text: String): Char {
        val header = text.lineSequence().firstOrNull().orEmpty()
        return if (header.count { it == ';' } > header.count { it == ',' }) ';' else ','
    }
}

/** Converte "79.90", "79,90" ou "1.234,56" (e "R$ 79,90") em número. */
fun parseMoney(raw: String?): Double? {
    val cleaned = raw?.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }?.takeIf { it.isNotEmpty() } ?: return null
    val normalized = when {
        cleaned.contains(',') && cleaned.contains('.') -> cleaned.replace(".", "").replace(',', '.')
        cleaned.contains(',') -> cleaned.replace(',', '.')
        else -> cleaned
    }
    return normalized.toDoubleOrNull()
}

/** Texto de preço como aparece na tela: "R$ 1.999", "R$ 79,90", "1.234,56". Ponto seguido de 3 dígitos é milhar. */
fun parseBrlText(raw: String?): Double? {
    val text = raw?.replace("R$", "")?.replace("\u00A0", " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val compact = text.replace(" ", "")
    return if (!compact.contains(',') && Regex("^\\d{1,3}(\\.\\d{3})+$").matches(compact)) {
        compact.replace(".", "").toDoubleOrNull()
    } else {
        parseMoney(compact)
    }
}
