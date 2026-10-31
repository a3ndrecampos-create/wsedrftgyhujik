@file:OptIn(ExperimentalMaterial3Api::class)

package com.voltapracasa.app

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// ---------- marca ----------

/** Pata desenhada em código (sem depender de pacote de ícones extra). */
@Composable
fun PawMark(boxSize: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(boxSize)) {
        val w = this.size.width
        val h = this.size.height
        drawOval(color, topLeft = Offset(w * 0.25f, h * 0.50f), size = Size(w * 0.50f, h * 0.40f))
        drawOval(color, topLeft = Offset(w * 0.04f, h * 0.38f), size = Size(w * 0.21f, h * 0.28f))
        drawOval(color, topLeft = Offset(w * 0.23f, h * 0.08f), size = Size(w * 0.21f, h * 0.30f))
        drawOval(color, topLeft = Offset(w * 0.56f, h * 0.08f), size = Size(w * 0.21f, h * 0.30f))
        drawOval(color, topLeft = Offset(w * 0.75f, h * 0.38f), size = Size(w * 0.21f, h * 0.28f))
    }
}

@Composable
fun BrandHeader(onRefresh: () -> Unit) {
    Surface(color = Brand.Ink) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PawMark(28.dp, Brand.Porch)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Volta Pra Casa", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Pets perdidos e encontrados", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar", tint = Color.White)
            }
        }
    }
}

@Composable
fun ScreenBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Surface(color = Brand.Ink) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
            Text(
                title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            actions()
        }
    }
}

/** Ícone de balão de conversa (o pacote básico de ícones não traz um). */
val ChatBubbleIcon: ImageVector = ImageVector.Builder(
    name = "ChatBubble", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).path(fill = SolidColor(Color.Black)) {
    moveTo(20f, 2f)
    horizontalLineTo(4f)
    curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
    verticalLineTo(22f)
    lineTo(6f, 18f)
    horizontalLineTo(20f)
    curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
    verticalLineTo(4f)
    curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
    close()
}.build()

// ---------- blocos básicos ----------

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.titleLarge)
}

@Composable
fun InfoTag(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun IconText(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.bodySmall
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = style, color = textColor)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PawMark(48.dp, MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private val ButtonShape = RoundedCornerShape(14.dp)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled, shape = ButtonShape) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled, shape = ButtonShape) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

// ---------- anúncios ----------

@Composable
fun TypeBadge(type: String, modifier: Modifier = Modifier, onPhoto: Boolean = false) {
    val dark = isSystemInDarkTheme()
    val base = Labels.typeColor(type)
    val bg = if (onPhoto) base else base.copy(alpha = if (dark) 0.22f else 0.14f)
    val fg = if (onPhoto) Color.White else Labels.typeInk(type, dark)
    Surface(modifier, color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            Labels.type(type), color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PostCard(p: Post, onClick: () -> Unit) {
    SurfaceCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box {
            AsyncImage(
                model = Net.photoUrl(p.photo), contentDescription = p.title(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(184.dp).background(MaterialTheme.colorScheme.surfaceVariant)
            )
            TypeBadge(p.type, onPhoto = true, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
            val tag: Pair<String, Color>? = when {
                p.review == "PENDING" -> "Em análise" to Color(0xFF8A5A00)
                p.review == "REJECTED" -> "Recusado" to Color(0xFFB42328)
                p.status != "ACTIVE" -> "Resolvido" to Color(0xFF1C7048)
                else -> null
            }
            if (tag != null) {
                Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), color = Color.White, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        tag.first, color = tag.second, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(p.title(), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOf(Labels.species(p.species), p.color, Labels.size(p.size)).filter { it.isNotBlank() }.joinToString(", ")
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                p.distanceKm?.let { IconText(Icons.Filled.Place, "a $it km") }
                Text(timeAgo(p.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (p.isOng) {
                IconText(
                    Icons.Filled.CheckCircle, "ONG verificada: ${p.ownerName}",
                    tint = Brand.Adoption, textColor = Brand.Adoption, style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun <T> FilterRow(options: List<Pair<String, T>>, selected: T, horizontalPadding: Dp = 16.dp, onSelect: (T) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { (label, value) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

// ---------- mapa ----------

fun dot(color: Int, size: Int): Drawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
    setStroke(6, android.graphics.Color.WHITE)
    setSize(size, size)
}

@Composable
fun MapScreen(posts: List<Post>, loc: Pair<Double, Double>?, onOpen: (String) -> Unit, topInset: Dp = 0.dp) {
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
                    icon = dot(0xFF2C3A94.toInt(), 38)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
            posts.forEach { p ->
                view.overlays.add(Marker(view).apply {
                    position = GeoPoint(p.lat, p.lng)
                    title = "${Labels.type(p.type)}: ${p.title()}"
                    icon = dot(Labels.typeColor(p.type).toArgb(), 60)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> onOpen(p.id); true }
                })
            }
            view.invalidate()
        })
        SmallFloatingActionButton(
            onClick = { loc?.let { mv.controller.animateTo(GeoPoint(it.first, it.second)) } },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = topInset + 12.dp, end = 12.dp)
        ) { Icon(Icons.Filled.LocationOn, contentDescription = "Centralizar na minha localização") }
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            shape = RoundedCornerShape(50), shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface
        ) {
            Text(
                if (posts.size == 1) "1 anúncio no mapa" else "${posts.size} anúncios no mapa",
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge
            )
        }
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
                Icon(
                    Icons.Filled.LocationOn, contentDescription = null, tint = Brand.Lost,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-22).dp).size(48.dp)
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                        Text("Arraste o mapa até o local onde o pet foi visto", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Confirmar este local", onClick = { mvRef?.mapCenter?.let { onPick(it.latitude, it.longitude) } })
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                }
            }
        }
    }
}

// ---------- segurança contra golpes ----------

private val SAFETY_TIPS = listOf(
    "Nunca pague para receber informação" to "Taxa, frete, PIX, depósito ou \"recompensa antecipada\" para dizer onde o pet está ou devolvê-lo é golpe. Quem encontrou de verdade não cobra.",
    "Converse só pelo app" to "O chat do Volta Pra Casa registra a conversa para investigar golpes. Desconfie de quem insiste em ir para WhatsApp, telefone ou links externos.",
    "Não envie códigos nem dados" to "Nunca passe códigos recebidos por SMS ou WhatsApp, senhas, CPF ou dados de cartão. É assim que clonam contas.",
    "Peça uma prova" to "Peça uma foto ou vídeo do pet no momento e confira detalhes que só o dono conhece, sem contar tudo antes.",
    "Encontro em local seguro" to "Combine em local público e movimentado, de preferência de dia e acompanhado. Se houver recompensa, só entregue depois de ter o pet de volta em mãos.",
    "Desconfie de pressa" to "Pressão, ameaças ou \"só hoje\" são sinais de golpe. Ninguém sério impede você de pensar.",
    "Denuncie" to "Use o botão Denunciar no anúncio. Anúncios com várias denúncias saem do ar."
)

/** Aviso fixo: aparece sempre, e ao tocar abre as dicas completas. */
@Composable
fun SafetyBanner(compact: Boolean = false, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val onC = MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 0.dp else 14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = onC, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Nunca pague por informações sobre um pet perdido.", style = MaterialTheme.typography.labelLarge, color = onC)
                Text("Quem cobra é golpista. Toque para ver dicas de segurança.", style = MaterialTheme.typography.bodySmall, color = onC)
            }
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Entendi") } },
            title = { Text("Como evitar golpes", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SAFETY_TIPS.forEach { (t, b) ->
                        Column {
                            Text(t, style = MaterialTheme.typography.titleMedium)
                            Text(b, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        )
    }
}
