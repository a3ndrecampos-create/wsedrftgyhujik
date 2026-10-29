package com.promoapp.domain.model

import java.net.URI

enum class DraftError {
    TITLE_TOO_SHORT,
    TITLE_TOO_LONG,
    INVALID_URL,
    INVALID_IMAGE_URL,
    MISSING_STORE,
    INVALID_PRICE,
    ORIGINAL_NOT_HIGHER,
    MISSING_AUTHOR,
}

/** Oferta em edição. As regras de validação ficam aqui, no domínio, para serem testadas sem Android. */
data class DealDraft(
    val title: String = "",
    val url: String = "",
    val store: String = "",
    val price: Double? = null,
    val originalPrice: Double? = null,
    val imageUrl: String = "",
    val couponCode: String = "",
    val category: String? = null,
    val freeShipping: Boolean = false,
    val authorName: String = "",
) {
    fun validate(): Set<DraftError> =
        buildSet {
            val titleLength = title.trim().length
            if (titleLength < MIN_TITLE_LENGTH) add(DraftError.TITLE_TOO_SHORT)
            if (titleLength > MAX_TITLE_LENGTH) add(DraftError.TITLE_TOO_LONG)
            if (!isHttpUrl(url)) add(DraftError.INVALID_URL)
            if (imageUrl.isNotBlank() && !isHttpUrl(imageUrl)) add(DraftError.INVALID_IMAGE_URL)
            if (store.isBlank()) add(DraftError.MISSING_STORE)
            if (price == null || price <= 0) add(DraftError.INVALID_PRICE)
            if (originalPrice != null && (price == null || originalPrice <= price)) add(DraftError.ORIGINAL_NOT_HIGHER)
            if (authorName.isBlank()) add(DraftError.MISSING_AUTHOR)
        }

    companion object {
        const val MIN_TITLE_LENGTH = 8
        const val MAX_TITLE_LENGTH = 120
    }
}

class InvalidDraftException(
    val errors: Set<DraftError>,
) : IllegalArgumentException("Rascunho inválido: $errors")

private fun isHttpUrl(value: String): Boolean =
    runCatching {
        val uri = URI(value.trim())
        (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
