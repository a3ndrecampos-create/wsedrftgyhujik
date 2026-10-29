package com.voltapracasa.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import kotlin.math.max

object Labels {
    val speciesList = listOf("DOG", "CAT", "BIRD", "RABBIT", "OTHER")

    fun type(t: String) = when (t) {
        "LOST" -> "Perdido"
        "FOUND" -> "Encontrado"
        "SIGHTING" -> "Avistado"
        "ADOPTION" -> "Adoção"
        else -> t
    }

    fun typeColor(t: String) = when (t) {
        "LOST" -> Color(0xFFE53935)
        "FOUND" -> Color(0xFF1E88E5)
        "SIGHTING" -> Color(0xFFFB8C00)
        "ADOPTION" -> Color(0xFF43A047)
        else -> Color.Gray
    }

    fun species(s: String) = when (s) {
        "DOG" -> "Cachorro"
        "CAT" -> "Gato"
        "BIRD" -> "Ave"
        "RABBIT" -> "Coelho"
        else -> "Outro"
    }

    fun size(s: String) = when (s) {
        "SMALL" -> "Pequeno"
        "MEDIUM" -> "Médio"
        "LARGE" -> "Grande"
        else -> ""
    }

    fun sex(s: String) = when (s) {
        "M" -> "Macho"
        "F" -> "Fêmea"
        else -> ""
    }
}

fun Post.title(): String = name.ifBlank { Labels.species(species) }

fun timeAgo(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60000
    return when {
        m < 1 -> "agora"
        m < 60 -> "há $m min"
        m < 1440 -> "há ${m / 60} h"
        else -> "há ${m / 1440} d"
    }
}

/** Reduz e corrige a rotação da foto antes do upload. */
fun compressImage(ctx: Context, uri: Uri): File? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val exif = ctx.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        val rot = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90f
            3 -> 180f
            8 -> 270f
            else -> 0f
        }
        val finalBmp = if (rot != 0f)
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
        else bmp
        val out = File(ctx.cacheDir, "up_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { finalBmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        out
    } catch (e: Exception) {
        null
    }
}

@SuppressLint("MissingPermission")
fun getLocation(ctx: Context, onResult: (Double, Double) -> Unit, onFail: () -> Unit) {
    try {
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { l ->
                if (l != null) onResult(l.latitude, l.longitude)
                else client.lastLocation.addOnSuccessListener { ll ->
                    if (ll != null) onResult(ll.latitude, ll.longitude) else onFail()
                }.addOnFailureListener { onFail() }
            }
            .addOnFailureListener { onFail() }
    } catch (e: SecurityException) {
        onFail()
    }
}
