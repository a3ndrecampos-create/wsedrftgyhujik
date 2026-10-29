package com.promoapp

import com.promoapp.domain.model.Deal

fun testDeal(
    price: Double = 50.0,
    originalPrice: Double? = 100.0,
) = Deal(
    id = 1,
    title = "Produto de teste",
    imageUrl = "",
    storeName = "Loja",
    price = price,
    originalPrice = originalPrice,
    authorName = "Autor",
    postedAtMillis = 0,
    temperature = 0,
    commentCount = 0,
    tags = emptyList(),
    url = "https://example.com",
)
