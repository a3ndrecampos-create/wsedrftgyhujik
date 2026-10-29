package com.promoserver

import com.promoserver.config.AppConfig
import com.promoserver.config.DotEnv
import com.promoserver.db.AppDatabase
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DotEnvTest {

    @Test
    fun `parses keys, quotes, comments, export and blank lines`() {
        val text = """
            ﻿# comentário
            PORT=80
            export ADMIN_TOKEN="segredo com espaços"
            NOME='aspas simples'
            COM_COMENTARIO=valor # depois
            VAZIO=
            URL=https://x.shardweb.app/a=b
            1INVALIDA=x
            sem igual
        """.trimIndent()

        val result = DotEnv.parse(text)

        assertEquals("80", result["PORT"])
        assertEquals("segredo com espaços", result["ADMIN_TOKEN"])
        assertEquals("aspas simples", result["NOME"])
        assertEquals("valor", result["COM_COMENTARIO"])
        assertEquals("", result["VAZIO"])
        assertEquals("https://x.shardweb.app/a=b", result["URL"])
        assertEquals(setOf("PORT", "ADMIN_TOKEN", "NOME", "COM_COMENTARIO", "VAZIO", "URL"), result.keys)
    }

    @Test
    fun `real environment variables win over the file`() {
        val dir = createTempDirectory("env")
        val file = dir.resolve("promo.env").toFile().apply { writeText("PORT=80\nPUBLIC_BASE_URL=https://do-arquivo.app\n") }

        val merged = DotEnv.merged(env = mapOf("PUBLIC_BASE_URL" to "https://do-ambiente.app", "ENV_FILE" to file.path))

        assertEquals("80", merged["PORT"])
        assertEquals("https://do-ambiente.app", merged["PUBLIC_BASE_URL"])
    }

    @Test
    fun `a missing file is fine`() {
        assertTrue(DotEnv.load("/caminho/que/nao/existe/promo.env").isEmpty())
        assertEquals(mapOf("A" to "1"), DotEnv.merged(env = mapOf("A" to "1", "ENV_FILE" to "/nao/existe.env")).filterKeys { it == "A" })
    }

    @Test
    fun `the shardcloud template produces a working configuration`() {
        val template = java.io.File("deploy/shardcloud/promo.env.example")
        val config = AppConfig.fromEnv(DotEnv.parse(template.readText()))

        assertEquals(80, config.port)
        assertEquals("/app/data/promo.db", config.dbPath)
        assertEquals("https://meupromoapp.shardweb.app", config.publicBaseUrl)
        assertTrue(config.trustProxy)
        assertTrue(config.adminToken == null) // o token vem vazio: o painel só liga depois que você preencher
        assertTrue(config.demoMode)
    }

    @Test
    fun `the database folder is created when missing`() {
        val dir = createTempDirectory("db")
        val path = dir.resolve("data/novo/promo.db").toString()

        AppDatabase.connect(path)

        assertTrue(java.io.File(path).isFile)
    }

    @Test
    fun `shardcloud file has the keys the platform requires`() {
        val keys = java.io.File("deploy/shardcloud/.shardcloud").readLines().filter { it.contains('=') }.associate {
            it.substringBefore('=') to it.substringAfter('=')
        }

        assertEquals("promo-server.jar", keys["MAIN"])
        assertTrue(keys.getValue("MEMORY").toInt() >= 512)
        assertTrue(keys.getValue("VERSION") in setOf("recommended", "latest"))
        assertTrue(keys.getValue("DISPLAY_NAME").length <= 50)
        assertTrue(keys.getValue("SUBDOMAIN").all { it.isLetterOrDigit() })
    }
}
