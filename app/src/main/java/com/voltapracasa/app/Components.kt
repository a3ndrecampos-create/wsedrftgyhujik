@file:OptIn(ExperimentalMaterial3Api::class)

package com.voltapracasa.app

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val LightColors = lightColorScheme(
    primary = Color(0xFF00796B),
    secondary = Color(0xFFFF8F00),
    tertiary = Color(0xFF5D4037)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    secondary = Color(0xFFFFB74D)
)

@Composable
fun VoltaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

@Composable
fun TypeBadge(type: String) {
    Surface(color = Labels.typeColor(type), shape = RoundedCornerShape(50)) {
        Text(
            Labels.type(type).uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun PostCard(p: Post, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Row {
            AsyncImage(
                model = Net.photoUrl(p.photo), contentDescription = p.title(),
                contentScale = ContentScale.Crop, modifier = Modifier.size(112.dp)
            )
            Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TypeBadge(p.type)
                Text(p.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(Labels.species(p.species), p.color).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall
                )
                val dist = p.distanceKm?.let { " • a $it km" } ?: ""
                Text("${timeAgo(p.createdAt)}$dist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.isOng) Text("✔ ONG: ${p.ownerName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        }
    }
}

@Composable
fun <T> FilterRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { (label, value) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

fun dot(color: Int, size: Int): Drawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
    setStroke(5, android.graphics.Color.WHITE)
    setSize(size, size)
}

@Composable
fun MapScreen(posts: List<Post>, loc: Pair<Double, Double>?, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val centered = remember { mutableStateOf(false) }
    val mv = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(-15.78, -47.93))
        }
    }
    DisposableEffect(Unit) {
        mv.onResume()
        onDispose { mv.onPause(); mv.onDetach() }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mv }, modifier = Modifier.fillMaxSize(), update = { view ->
            view.overlays.clear()
            loc?.let { (la, ln) ->
                if (!centered.value) {
                    view.controller.setCenter(GeoPoint(la, ln))
                    centered.value = true
                }
                view.overlays.add(Marker(view).apply {
                    position = GeoPoint(la, ln)
                    title = "Você está aqui"
                    icon = dot(0xFF607D8B.toInt(), 36)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
            posts.forEach { p ->
                view.overlays.add(Marker(view).apply {
                    position = GeoPoint(p.lat, p.lng)
                    title = "${Labels.type(p.type)}: ${p.title()}"
                    icon = dot(Labels.typeColor(p.type).toArgb(), 56)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> onOpen(p.id); true }
                })
            }
            view.invalidate()
        })
        SmallFloatingActionButton(
            onClick = { loc?.let { mv.controller.animateTo(GeoPoint(it.first, it.second)) } },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        ) { Text("📍") }
    }
}

@Composable
fun MapPickerDialog(initial: Pair<Double, Double>, onPick: (Double, Double) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            var mvRef by remember { mutableStateOf<MapView?>(null) }
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { c ->
                    MapView(c).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(17.0)
                        controller.setCenter(GeoPoint(initial.first, initial.second))
                        mvRef = this
                    }
                }, modifier = Modifier.fillMaxSize())
                Text("📍", fontSize = 40.sp, modifier = Modifier.align(Alignment.Center).offset(y = (-20).dp))
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp) {
                        Text("Arraste o mapa até o local onde o pet foi visto", Modifier.padding(10.dp), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { mvRef?.mapCenter?.let { onPick(it.latitude, it.longitude) } }) { Text("Confirmar este local") }
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                }
            }
        }
    }
}
