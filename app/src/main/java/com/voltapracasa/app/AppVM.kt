package com.voltapracasa.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

class AppVM(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("vpc", Context.MODE_PRIVATE)

    var user by mutableStateOf<User?>(null)
        private set
    var posts by mutableStateOf<List<Post>>(emptyList())
        private set
    var myPosts by mutableStateOf<List<Post>>(emptyList())
        private set
    var myMatches by mutableStateOf<List<MyMatch>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var loc by mutableStateOf<Pair<Double, Double>?>(null)
        private set
    var radiusKm by mutableStateOf(0)

    init {
        Net.token = prefs.getString("token", null)
        if (Net.token != null) {
            viewModelScope.launch {
                try { user = Net.api.me() } catch (e: Exception) {
                    if (e is HttpException && e.code() == 401) logout()
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            try {
                posts = Net.api.posts(
                    lat = loc?.first, lng = loc?.second,
                    radiusKm = if (radiusKm > 0 && loc != null) radiusKm.toDouble() else null
                )
            } catch (e: Exception) { message = e.friendly() }
            loading = false
        }
    }

    fun setLocation(lat: Double, lng: Double) {
        loc = lat to lng
        refresh()
    }

    fun setRadius(km: Int) {
        radiusKm = km
        refresh()
    }

    private fun onAuth(r: AuthResponse) {
        Net.token = r.token
        prefs.edit().putString("token", r.token).apply()
        user = r.user
        message = "Bem-vindo(a), ${r.user.name}!"
    }

    fun login(email: String, pw: String) {
        viewModelScope.launch {
            busy = true
            try { onAuth(Net.api.login(LoginBody(email.trim(), pw))) } catch (e: Exception) { message = e.friendly() }
            busy = false
        }
    }

    fun register(name: String, email: String, pw: String, ongName: String?) {
        viewModelScope.launch {
            busy = true
            try {
                onAuth(Net.api.register(RegisterBody(name.trim(), email.trim(), pw, if (ongName != null) "ONG" else "USER", ongName?.trim())))
            } catch (e: Exception) { message = e.friendly() }
            busy = false
        }
    }

    fun logout() {
        Net.token = null
        prefs.edit().remove("token").apply()
        user = null
        myPosts = emptyList()
        myMatches = emptyList()
    }

    fun loadMine() {
        if (user == null) return
        viewModelScope.launch {
            try {
                myPosts = Net.api.myPosts()
                myMatches = Net.api.myMatches()
            } catch (e: Exception) { message = e.friendly() }
            try { user = Net.api.me() } catch (_: Exception) {}
        }
    }

    fun createPost(uri: Uri, fields: Map<String, String>, done: (Post?) -> Unit) {
        viewModelScope.launch {
            busy = true
            var result: Post? = null
            try {
                val file = withContext(Dispatchers.IO) { compressImage(getApplication(), uri) }
                if (file == null) {
                    message = "Não foi possível ler a foto."
                } else {
                    val part = MultipartBody.Part.createFormData("photo", "photo.jpg", file.asRequestBody("image/jpeg".toMediaType()))
                    val body = fields.mapValues { it.value.toRequestBody("text/plain".toMediaType()) }
                    result = Net.api.create(part, body)
                    message = "Publicado! Vamos ajudar esse pet a voltar pra casa 🐾"
                    refresh()
                }
            } catch (e: Exception) { message = e.friendly() }
            busy = false
            done(result)
        }
    }

    fun resolve(id: String, note: String, done: (Post) -> Unit) {
        viewModelScope.launch {
            try { done(Net.api.resolve(id, ResolveBody(note))); refresh() } catch (e: Exception) { message = e.friendly() }
        }
    }

    fun delete(id: String, done: () -> Unit) {
        viewModelScope.launch {
            try { Net.api.delete(id); refresh(); done() } catch (e: Exception) { message = e.friendly() }
        }
    }

    fun report(id: String, reason: String) {
        viewModelScope.launch {
            try { Net.api.report(id, ReportBody(reason)); message = "Denúncia enviada. Obrigado por cuidar da comunidade." } catch (e: Exception) { message = e.friendly() }
        }
    }
}
