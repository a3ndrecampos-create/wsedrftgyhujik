package com.voltapracasa.app

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*
import java.io.IOException
import java.util.concurrent.TimeUnit

interface PetApi {
    @POST("api/auth/register") suspend fun register(@Body b: RegisterBody): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body b: LoginBody): AuthResponse
    @GET("api/me") suspend fun me(): User

    @GET("api/posts")
    suspend fun posts(
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radiusKm") radiusKm: Double? = null
    ): List<Post>

    @GET("api/posts/{id}") suspend fun post(@Path("id") id: String): Post

    @Multipart
    @POST("api/posts")
    suspend fun create(
        @Part photo: MultipartBody.Part,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>
    ): Post

    @POST("api/posts/{id}/resolve") suspend fun resolve(@Path("id") id: String, @Body b: ResolveBody): Post
    @DELETE("api/posts/{id}") suspend fun delete(@Path("id") id: String): OkResp
    @POST("api/posts/{id}/report") suspend fun report(@Path("id") id: String, @Body b: ReportBody): OkResp
    @GET("api/posts/{id}/matches") suspend fun matches(@Path("id") id: String): List<MatchItem>
    @GET("api/my/posts") suspend fun myPosts(): List<Post>
    @GET("api/my/matches") suspend fun myMatches(): List<MyMatch>
}

object Net {
    @Volatile var token: String? = null
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val base: String get() = BuildConfig.API_URL.trimEnd('/')

    val api: PetApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val b = chain.request().newBuilder()
                token?.let { b.header("Authorization", "Bearer $it") }
                chain.proceed(b.build())
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("$base/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PetApi::class.java)
    }

    fun photoUrl(path: String) = base + path
    fun shareUrl(id: String) = "$base/p/$id"

    fun parseError(e: HttpException): String {
        val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
        val msg = try { body?.let { json.decodeFromString<ApiError>(it).error } } catch (_: Exception) { null }
        return msg?.takeIf { it.isNotBlank() } ?: "Erro ${e.code()}"
    }
}

fun Throwable.friendly(): String = when (this) {
    is HttpException -> Net.parseError(this)
    is IOException -> "Sem conexão com o servidor. Verifique sua internet."
    else -> message ?: "Algo deu errado."
}
