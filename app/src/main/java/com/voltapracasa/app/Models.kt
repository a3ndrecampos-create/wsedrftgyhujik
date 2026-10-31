package com.voltapracasa.app

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "USER",
    val ongName: String? = null,
    val ongStatus: String? = null,
    val level: Int = 1,
    val approvedPosts: Int = 0,
    val nextLevelIn: Int = 0,
    val restricted: Boolean = false
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
    val distanceKm: Double? = null,
    val review: String = "APPROVED",
    val reviewNote: String = ""
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

@Serializable
data class ChatSummary(
    val id: String,
    val postId: String = "",
    val postName: String = "",
    val postSpecies: String = "OTHER",
    val postType: String = "LOST",
    val postPhoto: String = "",
    val isOwner: Boolean = false,
    val otherName: String = "",
    val lastText: String = "",
    val lastAt: Long = 0,
    val unread: Int = 0
)

@Serializable data class ChatMessage(val id: String, val mine: Boolean = false, val text: String = "", val at: Long = 0, val flag: String = "")
@Serializable data class ChatThread(val chat: ChatSummary, val messages: List<ChatMessage> = emptyList())
@Serializable data class OpenChatBody(val postId: String)
@Serializable data class SendBody(val text: String)
@Serializable data class ForgotBody(val email: String)
@Serializable data class ResetBody(val email: String, val code: String, val password: String)
