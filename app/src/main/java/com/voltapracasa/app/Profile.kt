@file:OptIn(ExperimentalMaterial3Api::class)

package com.voltapracasa.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable

@Composable
fun ProfileTab(vm: AppVM, onOpen: (String) -> Unit) {
    val u = vm.user
    if (u == null) {
        AuthForm(vm)
        return
    }
    LaunchedEffect(u.id) { vm.loadMine() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("👤 ${u.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(u.email, style = MaterialTheme.typography.bodySmall)
        if (u.role == "ONG") {
            val s = when (u.ongStatus) {
                "APPROVED" -> "✔ ONG aprovada: você pode publicar pets para adoção."
                "REJECTED" -> "✖ Cadastro de ONG não aprovado."
                else -> "⏳ ONG aguardando aprovação da equipe. Você já pode usar o app normalmente."
            }
            Card { Text(s, Modifier.padding(12.dp)) }
        }

        if (vm.myMatches.isNotEmpty()) {
            Text("🎯 Possíveis encontros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            vm.myMatches.forEach { m ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(m.match.post.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${m.match.score}% • seu anúncio \"${m.mine.title()}\" (${Labels.type(m.mine.type)})", fontWeight = FontWeight.Bold)
                        Text("pode combinar com um pet ${Labels.type(m.match.post.type).lowercase()} a ${m.match.distanceKm} km. Toque para ver.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text("Meus anúncios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (vm.myPosts.isEmpty()) Text("Você ainda não publicou nada.", style = MaterialTheme.typography.bodySmall)
        vm.myPosts.forEach { p -> PostCard(p) { onOpen(p.id) } }

        OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Sair da conta") }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun AuthForm(vm: AppVM) {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var isOng by rememberSaveable { mutableStateOf(false) }
    var ongName by rememberSaveable { mutableStateOf("") }
    var terms by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🐾 Volta Pra Casa", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(if (register) "Crie sua conta gratuita" else "Entre para publicar e ver contatos")
        if (register) {
            Field("Seu nome", name) { name = it }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = isOng, onCheckedChange = { isOng = it })
                Text("Sou ONG / protetor(a) de animais")
            }
            if (isOng) {
                Field("Nome da ONG", ongName) { ongName = it }
                Text("Sua ONG será analisada pela equipe antes de poder publicar pets para adoção.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Field("E-mail", email, kb = KeyboardType.Email) { email = it }
        OutlinedTextField(
            value = pass, onValueChange = { pass = it }, label = { Text("Senha (mín. 6 caracteres)") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        if (register) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = terms, onCheckedChange = { terms = it })
                Text("Concordo que é proibido vender animais e que denúncias podem remover meus anúncios.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            enabled = !vm.busy && (!register || terms), modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (register) vm.register(name, email, pass, if (isOng) ongName.ifBlank { name } else null)
                else vm.login(email, pass)
            }
        ) { Text(if (register) "Criar conta" else "Entrar") }
        TextButton(onClick = { register = !register }, modifier = Modifier.fillMaxWidth()) {
            Text(if (register) "Já tenho conta" else "Criar conta")
        }
    }
}
