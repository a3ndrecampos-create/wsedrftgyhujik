package com.promoserver.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/** Seletores CSS para páginas que não trazem dados estruturados (JSON-LD ou JSON embutido). */
@Serializable
data class CssSelectors(
    val item: String,
    val title: String,
    val price: String,
    val oldPrice: String? = null,
    val link: String = "a",
    val image: String = "img",
)

/** Uma loja lida direto das páginas públicas. Configurada no arquivo sites.json. */
@Serializable
data class ScrapeSiteConfig(
    val store: String,
    val urls: List<String>,
    val enabled: Boolean = true,
    /** auto, jsonld, state ou css. */
    val strategy: String = "auto",
    val css: CssSelectors? = null,
    /** Use {url} onde entra o link do produto. Quando virar afiliado, é só trocar o modelo. */
    val affiliateUrlTemplate: String = "{url}",
    val maxItems: Int = 60,
)

object ScrapeSites {
    private val log = LoggerFactory.getLogger(ScrapeSites::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<ScrapeSiteConfig> = json.decodeFromString<List<ScrapeSiteConfig>>(text)

    /** Lê o arquivo. Se não existir ou estiver com erro, devolve lista vazia e avisa no log. */
    fun load(path: String?): List<ScrapeSiteConfig> {
        val file = path?.let(::File)?.takeIf { it.isFile } ?: return emptyList()
        return try {
            parse(file.readText()).filter { it.enabled && it.urls.isNotEmpty() }
        } catch (e: Exception) {
            log.error("Arquivo de sites inválido ({}): {}", path, e.message)
            emptyList()
        }
    }
}
