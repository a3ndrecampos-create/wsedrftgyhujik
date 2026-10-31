@file:OptIn(ExperimentalMaterial3Api::class)

package com.voltapracasa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

private fun chatTitle(c: ChatSummary) = c.postName.ifBlank { Labels.species(c.postSpecies) }

private fun flagText(flag: String) = when (flag) {
    "money" -> "Cuidado: esta mensagem fala de pagamento. Nunca pague por informações sobre um pet perdido."
    "code" -> "Nunca informe códigos recebidos por SMS ou WhatsApp. É golpe para clonar sua conta."
    else -> "Esta mensagem tem telefone ou link. Prefira conversar só por aqui: fora do app não dá para investigar golpes."
}

// ---------- aba "Conversas" ----------

@Composable
fun ChatsTab(vm: AppVM, onOpen: (String) -> Unit) {
    val u = vm.user
    if (u == null) {
        EmptyState("Entre para conversar", "Faça login na aba Perfil para falar com quem publicou um anúncio.")
        return
    }
    LaunchedEffect(u.id) { vm.loadChats() }
    LazyColumn(
        contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Conversas", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "As mensagens são apagadas após 7 dias ou quando a entrega é concluída.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (vm.chats.isEmpty()) {
            item { EmptyState("Nenhuma conversa ainda", "Toque em \"Conversar pelo app\" dentro de um anúncio para falar com quem publicou.") }
        }
        items(vm.chats, key = { it.id }) { c -> ChatRow(c) { onOpen(c.id) } }
    }
}

@Composable
private fun ChatRow(c: ChatSummary, onClick: () -> Unit) {
    SurfaceCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = Net.photoUrl(c.postPhoto), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.otherName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Labels.type(c.postType)}: ${chatTitle(c)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (c.lastText.isNotBlank()) {
                    Text(
                        c.lastText, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(timeAgo(c.lastAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.unread > 0) {
                    Spacer(Modifier.height(4.dp))
                    Surface(shape = CircleShape, color = Brand.Porch) {
                        Text(
                            "${c.unread}", Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            color = Brand.Ink, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ---------- tela de conversa ----------

@Composable
fun ChatScreen(vm: AppVM, chatId: String, onBack: () -> Unit, onOpenPost: (String) -> Unit) {
    var thread by remember(chatId) { mutableStateOf<ChatThread?>(null) }
    var text by remember(chatId) { mutableStateOf("") }
    var sending by remember(chatId) { mutableStateOf(false) }
    var dialog by remember(chatId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun load() {
        try {
            thread = Net.api.chatMessages(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 404 || e.code() == 410) {
                vm.message = "Esta conversa foi encerrada e apagada."
                onBack()
            }
        } catch (_: Exception) {
            // sem internet: tenta de novo no próximo ciclo
        }
    }

    LaunchedEffect(chatId) {
        while (true) {
            load()
            delay(4000)
        }
    }

    val count = thread?.messages?.size ?: 0
    LaunchedEffect(count) { if (count > 0) listState.scrollToItem(count - 1) }

    fun send() {
        val msg = text.trim()
        if (msg.isEmpty() || sending) return
        sending = true
        scope.launch {
            try {
                Net.api.sendChat(chatId, SendBody(msg))
                text = ""
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                vm.message = e.friendly()
            }
            sending = false
        }
    }

    val t = thread
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenBar(t?.chat?.otherName ?: "Conversa", onBack) {
            IconButton(onClick = { dialog = "report" }) {
                Icon(Icons.Filled.Warning, contentDescription = "Denunciar conversa", tint = Color.White)
            }
        }
        SafetyBanner(compact = true)

        if (t == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val c = t.chat
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenPost(c.postId) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = Net.photoUrl(c.postPhoto), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chatTitle(c), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${Labels.type(c.postType)}, toque para ver o anúncio", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (c.isOwner) TextButton(onClick = { dialog = "done" }) { Text("Entrega concluída") }
                }
            }

            if (t.messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Diga olá! Combine tudo por aqui. As mensagens são apagadas após 7 dias ou quando a entrega for concluída.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(), state = listState,
                    contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(t.messages, key = { it.id }) { m -> Bubble(m) }
                }
            }

            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it.take(1000) },
                        modifier = Modifier.weight(1f), placeholder = { Text("Escreva uma mensagem") },
                        maxLines = 4, shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    val canSend = text.isNotBlank() && !sending
                    IconButton(
                        onClick = { send() }, enabled = canSend,
                        modifier = Modifier.padding(bottom = 4.dp).size(48.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (canSend) 1f else 0.4f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }

    when (dialog) {
        "report" -> AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } },
            title = { Text("O que aconteceu?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Pediu dinheiro ou pagamento", "Pediu código ou dados pessoais",
                        "Tentou me levar para fora do app", "Ofensa ou ameaça", "Outro motivo"
                    ).forEach { r ->
                        SecondaryButton(r, onClick = { dialog = null; vm.reportChat(chatId, r) })
                    }
                }
            }
        )
        "done" -> {
            val chat = thread?.chat
            if (chat != null) {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text("Entrega concluída?") },
                    text = { Text("O anúncio sai do mapa e todas as conversas dele são apagadas agora.") },
                    confirmButton = {
                        TextButton(onClick = {
                            dialog = null
                            vm.resolve(chat.postId, if (chat.postType == "ADOPTION") "(adotado)" else "(reencontrado)") { onBack() }
                        }) { Text("Confirmar") }
                    },
                    dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } }
                )
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (m.mine) 16.dp else 4.dp, bottomEnd = if (m.mine) 4.dp else 16.dp
    )
    val textColor = if (m.mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start) {
        Surface(
            shape = shape,
            color = if (m.mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            shadowElevation = if (m.mine) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(m.text, style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text(timeAgo(m.at), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
            }
        }
        if (m.flag.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = Brand.Lost.copy(alpha = 0.12f), modifier = Modifier.widthIn(max = 300.dp)) {
                Text(flagText(m.flag), Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
