@file:OptIn(ExperimentalMaterial3Api::class)

package com.voltapracasa.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

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

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 4.dp)) { Text("← Voltar") }
        val p = post
        if (p == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val f = failed
                if (f != null) Text(f, Modifier.padding(24.dp)) else CircularProgressIndicator()
            }
        } else {
            val mine = vm.user?.id == p.userId
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AsyncImage(
                    model = Net.photoUrl(p.photo), contentDescription = p.title(),
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(300.dp)
                )
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeBadge(p.type)
                        if (p.status != "ACTIVE") Text("✔ Resolvido ${p.resolvedNote}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text(p.title(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val info = listOf(Labels.species(p.species), p.color, Labels.size(p.size), Labels.sex(p.sex)).filter { it.isNotBlank() }
                    Text(info.joinToString(" • "), style = MaterialTheme.typography.bodyMedium)
                    if (p.description.isNotBlank()) Text(p.description)
                    if (p.address.isNotBlank()) Text("📍 ${p.address}")
                    Text(
                        "${timeAgo(p.createdAt)} • por ${p.ownerName}${if (p.isOng) " (ONG verificada)" else ""}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (p.contactPhone.isNotBlank()) {
                        val digits = p.contactPhone.filter { it.isDigit() }.let { if (it.length in 10..11) "55$it" else it }
                        Button(onClick = { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))) }, modifier = Modifier.fillMaxWidth()) {
                            Text("💬 Chamar no WhatsApp")
                        }
                        OutlinedButton(onClick = { safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.contactPhone}"))) }, modifier = Modifier.fillMaxWidth()) {
                            Text("📞 Ligar")
                        }
                    } else if (vm.user == null) {
                        Text("Entre na sua conta (aba Perfil) para ver o contato.", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = {
                        safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}(${Uri.encode(p.title())})")))
                    }, modifier = Modifier.fillMaxWidth()) { Text("🗺️ Abrir rota no mapa") }
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "🐾 ${Labels.type(p.type).uppercase()}: ${p.title()} ${p.address}\n${Net.shareUrl(p.id)}")
                        }
                        safeStart(Intent.createChooser(send, "Compartilhar"))
                    }, modifier = Modifier.fillMaxWidth()) { Text("🔗 Compartilhar (ajude a divulgar)") }

                    if (matches.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("🎯 Possíveis correspondências perto daqui", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        matches.forEach { m ->
                            Text("${m.score}% de compatibilidade • a ${m.distanceKm} km", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            PostCard(m.post) { onOpen(m.post.id) }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    if (mine) {
                        if (p.status == "ACTIVE") Button(onClick = { dialog = "resolve" }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (p.type == "ADOPTION") "🏡 Marcar como adotado" else "🎉 Marcar como resolvido")
                        }
                        TextButton(onClick = { dialog = "delete" }, modifier = Modifier.fillMaxWidth()) { Text("Excluir anúncio") }
                    } else if (vm.user != null) {
                        TextButton(onClick = { dialog = "report" }, modifier = Modifier.fillMaxWidth()) { Text("🚩 Denunciar (venda, golpe, falso)") }
                    }
                    Spacer(Modifier.height(24.dp))
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
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Venda de animal", "Golpe ou spam", "Informação falsa", "Conteúdo impróprio").forEach { r ->
                                OutlinedButton(onClick = { dialog = null; vm.report(p.id, r) }, modifier = Modifier.fillMaxWidth()) { Text(r) }
                            }
                        }
                    }
                )
            }
        }
    }
}
