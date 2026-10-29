package com.promoapp.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SearchRoute

@Serializable
data object CouponsRoute

@Serializable
data object AlertsRoute

@Serializable
data object ProfileRoute

@Serializable
data object PostDealRoute

@Serializable
data class DealDetailRoute(
    val dealId: Long,
)
