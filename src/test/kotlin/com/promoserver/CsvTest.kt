package com.promoserver

import com.promoserver.collectors.Csv
import com.promoserver.collectors.parseMoney
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CsvTest {

    @Test
    fun `parses quoted fields with commas, quotes and line breaks`() {
        val rows = Csv.parse("a,b\n\"x, y\",\"he said \"\"hi\"\"\"\n\"linha1\nlinha2\",z\n")

        assertEquals(listOf("a", "b"), rows[0])
        assertEquals(listOf("x, y", "he said \"hi\""), rows[1])
        assertEquals(listOf("linha1\nlinha2", "z"), rows[2])
    }

    @Test
    fun `handles windows line endings and a missing final newline`() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("a,b\r\n1,2"))
    }

    @Test
    fun `detects semicolon delimiter`() {
        assertEquals(';', Csv.detectDelimiter("a;b;c\n1;2;3"))
        assertEquals(',', Csv.detectDelimiter("a,b,c\n1,2,3"))
    }

    @Test
    fun `parses money in different formats`() {
        assertEquals(79.9, parseMoney("79.90"))
        assertEquals(79.9, parseMoney("79,90"))
        assertEquals(1234.56, parseMoney("1.234,56"))
        assertEquals(79.9, parseMoney("R$ 79,90"))
        assertNull(parseMoney(""))
        assertNull(parseMoney("abc"))
        assertNull(parseMoney(null))
    }
}
