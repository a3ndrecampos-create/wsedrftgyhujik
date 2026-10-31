@file:OptIn(ExperimentalMaterial3Api::class)

package com.voltapracasa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ProfileTab(vm: AppVM, onOpen: (String) -> Unit) {
    val u = vm.user
    if (u == null) {
        AuthForm(vm, compact = true)
        return
    }
    LaunchedEffect(u.id) { vm.loadMine() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SurfaceCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Brand.Ink), contentAlignment = Alignment.Center) {
                    Text(u.name.take(1).uppercase(), color = Brand.Porch, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(u.name, style = MaterialTheme.typography.titleLarge)
                    Text(u.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        val levelText = when {
            u.level >= 3 -> "Nível 3: seus anúncios entram no ar na hora."
            u.restricted -> "Nível 1: seus anúncios passam por verificação de foto enquanto houver denúncia pendente em algum anúncio seu."
            else -> "Nível ${u.level} de 3: seus anúncios passam por verificação de foto antes de ir ao ar. " +
                "Faltam ${u.nextLevelIn} ${if (u.nextLevelIn == 1) "anúncio aprovado" else "anúncios aprovados"} para o próximo nível."
        }
        SurfaceCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${u.level}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Seu nível", style = MaterialTheme.typography.titleMedium)
                    Text(levelText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (u.role == "ONG") {
            val approved = u.ongStatus == "APPROVED"
            val rejected = u.ongStatus == "REJECTED"
            val tint = when {
                approved -> Brand.Adoption
                rejected -> Brand.Lost
                else -> Brand.Porch
            }
            val iconTint = if (approved || rejected) tint else Color(0xFF9A6400)
            val msg = when {
                approved -> "ONG aprovada: você pode publicar pets para adoção."
                rejected -> "Cadastro de ONG não aprovado."
                else -> "ONG aguardando aprovação da equipe. Você já pode usar o app normalmente."
            }
            Surface(shape = RoundedCornerShape(14.dp), color = tint.copy(alpha = 0.16f)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (approved) Icons.Filled.CheckCircle else if (rejected) Icons.Filled.Warning else Icons.Filled.Info, contentDescription = null, tint = iconTint)
                    Spacer(Modifier.width(12.dp))
                    Text(msg, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (vm.myMatches.isNotEmpty()) {
            SectionTitle("Possíveis encontros")
            vm.myMatches.forEach { m ->
                SurfaceCard(Modifier.fillMaxWidth().clickable { onOpen(m.match.post.id) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = Brand.Porch) {
                            Text(
                                "${m.match.score}%", Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                color = Brand.Ink, style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Seu anúncio: ${m.mine.title()}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Pode combinar com um pet ${Labels.type(m.match.post.type).lowercase()} a ${m.match.distanceKm} km. Toque para ver.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        SectionTitle("Meus anúncios")
        if (vm.myPosts.isEmpty()) {
            EmptyState("Nenhum anúncio ainda", "Use o botão Publicar para avisar a vizinhança.")
        }
        vm.myPosts.forEach { p -> PostCard(p) { onOpen(p.id) } }

        SecondaryButton("Sair da conta", onClick = { vm.logout() })
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(4.dp)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val shape = RoundedCornerShape(10.dp)
                Surface(
                    shape = shape,
                    color = if (on) MaterialTheme.colorScheme.surface else Color.Transparent,
                    modifier = Modifier.weight(1f).clip(shape).clickable { onSelect(i) }
                ) {
                    Text(
                        label, Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AuthForm(vm: AppVM, compact: Boolean = false) {
    // 0 = entrar, 1 = criar conta, 2 = esqueci a senha, 3 = digitar código e nova senha
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var showPass by rememberSaveable { mutableStateOf(false) }
    var isOng by rememberSaveable { mutableStateOf(false) }
    var ongName by rememberSaveable { mutableStateOf("") }
    var terms by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!compact) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Ink), contentAlignment = Alignment.Center) {
                PawMark(36.dp, Brand.Porch)
            }
            Text("Volta Pra Casa", style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            when (mode) {
                1 -> "Crie sua conta gratuita para publicar anúncios."
                2 -> "Informe o e-mail da sua conta. Enviaremos um código de 6 dígitos para você criar uma nova senha."
                3 -> "Digite o código enviado para ${email.trim()} e escolha uma nova senha. O código vale por 15 minutos."
                else -> "Entre para publicar anúncios e conversar com quem ajuda."
            },
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (mode <= 1) Segmented(listOf("Entrar", "Criar conta"), mode) { mode = it }

        if (mode == 1) {
            Field("Seu nome", name) { name = it }
            Row(Modifier.fillMaxWidth().clickable { isOng = !isOng }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isOng, onCheckedChange = { isOng = it })
                Text("Sou ONG ou protetor(a) de animais", style = MaterialTheme.typography.bodyMedium)
            }
            if (isOng) {
                Field("Nome da ONG", ongName) { ongName = it }
                Text(
                    "Sua ONG será analisada pela equipe antes de poder publicar pets para adoção.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (mode != 3) Field("E-mail", email, kb = KeyboardType.Email) { email = it }

        if (mode == 3) {
            Field("Código de 6 dígitos", code, kb = KeyboardType.Number) { code = it.filter { ch -> ch.isDigit() }.take(6) }
        }
        if (mode != 2) {
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text(if (mode == 3) "Nova senha (mín. 6 caracteres)" else "Senha (mín. 6 caracteres)") },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                trailingIcon = { TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "Ocultar" else "Mostrar") } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }

        if (mode == 0) {
            TextButton(onClick = { mode = 2 }, modifier = Modifier.align(Alignment.End)) { Text("Esqueci minha senha") }
        }
        if (mode == 1) {
            Row(Modifier.fillMaxWidth().clickable { terms = !terms }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = terms, onCheckedChange = { terms = it })
                Text(
                    "Concordo que é proibido vender animais ou cobrar por informações de um pet, e que denúncias podem remover meus anúncios.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        when (mode) {
            2 -> {
                PrimaryButton(
                    if (vm.busy) "Aguarde…" else "Enviar código",
                    enabled = !vm.busy && email.isNotBlank(),
                    onClick = { vm.forgot(email) { code = ""; pass = ""; mode = 3 } }
                )
                TextButton(onClick = { mode = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao login") }
            }
            3 -> {
                PrimaryButton(
                    if (vm.busy) "Aguarde…" else "Redefinir senha",
                    enabled = !vm.busy && code.length == 6 && pass.length >= 6,
                    onClick = { vm.reset(email, code, pass) }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { vm.forgot(email) {} }, enabled = !vm.busy) { Text("Reenviar código") }
                    TextButton(onClick = { mode = 0 }) { Text("Voltar ao login") }
                }
            }
            else -> PrimaryButton(
                if (vm.busy) "Aguarde…" else if (mode == 1) "Criar conta" else "Entrar",
                enabled = !vm.busy && (mode == 0 || terms),
                onClick = {
                    if (mode == 1) vm.register(name, email, pass, if (isOng) ongName.ifBlank { name } else null)
                    else vm.login(email, pass)
                }
            )
        }
    }
}
