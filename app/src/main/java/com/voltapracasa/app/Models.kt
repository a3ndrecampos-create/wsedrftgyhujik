package com.voltapracasa.app

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "USER",
    val ongName: String? = null,
    val ongStatus: String? = null
)

@Serializable
data class Post(
    val id: String,
    val userId: String = "",
    val type: String,
    val species: String = "OTHER",
    val name: String = "",
    val color: String = "",
    val size: String = "",
    val sex: String = "",
    val description: String = "",
    val contactPhone: String = "",
    val address: String = "",
    val lat: Double,
    val lng: Double,
    val photo: String,
    val status: String = "ACTIVE",
    val resolvedNote: String = "",
    val createdAt: Long = 0,
    val ownerName: String = "",
    val isOng: Boolean = false,
    val distanceKm: Double? = null
)

@Serializable data class MatchItem(val post: Post, val score: Int = 0, val distanceKm: Double = 0.0)
@Serializable data class MyMatch(val mine: Post, val match: MatchItem)
@Serializable data class AuthResponse(val token: String, val user: User)
@Serializable data class LoginBody(val email: String, val password: String)
@Serializable data class RegisterBody(val name: String, val email: String, val password: String, val role: String, val ongName: String? = null)
@Serializable data class ResolveBody(val note: String)
@Serializable data class ReportBody(val reason: String)
@Serializable data class OkResp(val ok: Boolean = true)
@Serializable data class ApiError(val error: String = "")
