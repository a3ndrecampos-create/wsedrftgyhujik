@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.voltapracasa.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape, color = Color.Black.copy(alpha = 0.45f),
        modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun DetailScreen(vm: AppVM, id: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var post by remember(id) { mutableStateOf<Post?>(null) }
    var matches by remember(id) { mutableStateOf<List<MatchItem>>(emptyList()) }
    var failed by remember(id) { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id, vm.user?.id) {
        try { post = Net.api.post(id) } catch (e: Exception) { failed = e.friendly() }
        try { matches = Net.api.matches(id) } catch (_: Exception) {}
    }

    fun safeStart(i: Intent) {
        try { ctx.startActivity(i) } catch (e: Exception) { vm.message = "Nenhum app encontrado para abrir isso." }
    }

    val p = post
    if (p == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenBar("Anúncio", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val f = failed
                if (f != null) Text(f, Modifier.padding(24.dp)) else CircularProgressIndicator()
            }
        }
        return
    }

    val mine = vm.user?.id == p.userId

    fun share() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "🐾 ${Labels.type(p.type).uppercase()}: ${p.title()} ${p.address}\n${Net.shareUrl(p.id)}")
        }
        safeStart(Intent.createChooser(send, "Compartilhar"))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box {
                AsyncImage(
                    model = Net.photoUrl(p.photo), contentDescription = p.title(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(340.dp).background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.40f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.30f)))
                    )
                )
                TypeBadge(p.type, onPhoto = true, modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 40.dp))
            }

            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier.offset(y = (-24).dp).fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (p.status != "ACTIVE") {
                        val note = p.resolvedNote.trim('(', ')')
                        Surface(shape = RoundedCornerShape(12.dp), color = Brand.Adoption.copy(alpha = 0.14f)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Brand.Adoption)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (note.isBlank()) "Anúncio resolvido" else "Anúncio resolvido: $note",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    Text(p.title(), style = MaterialTheme.typography.headlineLarge)

                    val tags = listOf(Labels.species(p.species), p.color, Labels.size(p.size), Labels.sex(p.sex)).filter { it.isNotBlank() }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { InfoTag(it) }
                    }

                    if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodyLarge)

                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (p.address.isNotBlank()) {
                                IconText(Icons.Filled.Place, p.address, textColor = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconText(
                                Icons.Filled.Person, "por ${p.ownerName}, ${timeAgo(p.createdAt)}",
                                textColor = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium
                            )
                            if (p.isOng) {
                                IconText(
                                    Icons.Filled.CheckCircle, "ONG verificada",
                                    tint = Brand.Adoption, textColor = Brand.Adoption, style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    val hasPhone = p.contactPhone.isNotBlank()
                    if (hasPhone) {
                        val digits = p.contactPhone.filter { it.isDigit() }.let { if (it.length in 10..11) "55$it" else it }
                        PrimaryButton(
                            "Chamar no WhatsApp", icon = Icons.AutoMirrored.Filled.Send,
                            onClick = { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))) }
                        )
                    } else if (vm.user == null) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Entre na sua conta (aba Perfil) para ver o contato.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (hasPhone) {
                            SecondaryButton(
                                "Ligar", icon = Icons.Filled.Call, modifier = Modifier.weight(1f),
                                onClick = { safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.contactPhone}"))) }
                            )
                        }
                        SecondaryButton(
                            "Ver rota", icon = Icons.Filled.Place, modifier = Modifier.weight(1f),
                            onClick = {
                                safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}(${Uri.encode(p.title())})")))
                            }
                        )
                    }
                    SecondaryButton("Compartilhar e ajudar a divulgar", icon = Icons.Filled.Share, onClick = { share() })

                    if (matches.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        SectionTitle("Possíveis correspondências")
                        matches.forEach { m ->
                            Text(
                                "${m.score}% de compatibilidade, a ${m.distanceKm} km",
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                            )
                            PostCard(m.post) { onOpen(m.post.id) }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    if (mine) {
                        if (p.status == "ACTIVE") {
                            PrimaryButton(
                                if (p.type == "ADOPTION") "Marcar como adotado" else "Marcar como resolvido",
                                icon = Icons.Filled.CheckCircle, onClick = { dialog = "resolve" }
                            )
                        }
                        TextButton(
                            onClick = { dialog = "delete" }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Excluir anúncio") }
                    } else if (vm.user != null) {
                        TextButton(onClick = { dialog = "report" }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Denunciar (venda, golpe, falso)")
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onBack)
            RoundIconButton(Icons.Filled.Share, "Compartilhar") { share() }
        }
    }

    when (dialog) {
        "resolve" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Que bom! Confirmar?") },
            text = { Text("O anúncio sai do mapa e fica marcado como resolvido.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    vm.resolve(p.id, if (p.type == "ADOPTION") "(adotado)" else "(reencontrado)") { post = it }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } }
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Excluir anúncio?") },
            text = { Text("Esta ação não pode ser desfeita.") },
            confirmButton = { TextButton(onClick = { dialog = null; vm.delete(p.id) { onBack() } }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } }
        )
        "report" -> AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } },
            title = { Text("Qual o problema?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Venda de animal", "Golpe ou spam", "Informação falsa", "Conteúdo impróprio").forEach { r ->
                        SecondaryButton(r, onClick = { dialog = null; vm.report(p.id, r) })
                    }
                }
            }
        )
    }
}
