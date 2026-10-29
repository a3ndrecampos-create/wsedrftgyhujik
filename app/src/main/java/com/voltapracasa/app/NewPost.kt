@file:OptIn(ExperimentalMaterial3Api::class)

package com.voltapracasa.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, lines: Int = 1, kb: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), minLines = lines, maxLines = if (lines > 1) 6 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = kb)
    )
}

@Composable
fun NewPostScreen(vm: AppVM, initialType: String, onBack: () -> Unit, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    var type by rememberSaveable { mutableStateOf(initialType) }
    var species by rememberSaveable { mutableStateOf("DOG") }
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var camUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var coords by remember { mutableStateOf(vm.loc) }
    var picking by remember { mutableStateOf(false) }

    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) photoUri = camUri }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) photoUri = u }

    fun takePhoto() {
        val dir = File(ctx.cacheDir, "images").apply { mkdirs() }
        val f = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        camUri = uri
        cam.launch(uri)
    }

    LaunchedEffect(Unit) {
        if (initialType == "SIGHTING" && photoUri == null) takePhoto()
        if (coords == null) getLocation(ctx, { a, b -> coords = a to b }, {})
    }

    val types = buildList {
        add("🔍 Perdi" to "LOST"); add("📸 Avistei" to "SIGHTING"); add("🤝 Estou com ele" to "FOUND")
        if (vm.user?.role == "ONG" && vm.user?.ongStatus == "APPROVED") add("🏠 Adoção" to "ADOPTION")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack) { Text("← Cancelar") }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Novo anúncio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            FilterRow(types, type) { type = it }

            Text(
                when (type) {
                    "LOST" -> "Conte como seu pet é. Quanto mais detalhes, mais fácil de cruzar com avistamentos."
                    "SIGHTING" -> "Tire uma foto agora: o ping aparece no mapa na hora, no local onde você está."
                    "FOUND" -> "Você está cuidando de um pet achado? Avise para o dono encontrar."
                    else -> "Adoção responsável. É proibido cobrar ou vender animais."
                },
                style = MaterialTheme.typography.bodySmall
            )

            if (photoUri != null) {
                AsyncImage(model = photoUri, contentDescription = "Foto do pet", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { takePhoto() }) { Text("📷 Tirar foto") }
                OutlinedButton(onClick = { gallery.launch("image/*") }) { Text("🖼️ Galeria") }
            }

            Text("Espécie", fontWeight = FontWeight.Bold)
            FilterRow(Labels.speciesList.map { Labels.species(it) to it }, species) { species = it }
            if (type != "SIGHTING") Field(if (type == "LOST") "Nome do pet" else "Nome (se souber)", name) { name = it }
            Field("Cor / pelagem (ex.: caramelo, preto e branco)", color) { color = it }
            Text("Porte", fontWeight = FontWeight.Bold)
            FilterRow(listOf("Não sei" to "", "Pequeno" to "SMALL", "Médio" to "MEDIUM", "Grande" to "LARGE"), size) { size = it }
            Text("Sexo", fontWeight = FontWeight.Bold)
            FilterRow(listOf("Não sei" to "", "Macho" to "M", "Fêmea" to "F"), sex) { sex = it }
            Field("Detalhes (coleira, manchas, comportamento...)", desc, lines = 3) { desc = it }
            Field("Seu WhatsApp com DDD (visível só para usuários logados)", phone, kb = KeyboardType.Phone) { phone = it }
            Field("Referência do local (rua, bairro)", address) { address = it }

            val c = coords
            Text(if (c == null) "📍 Obtendo sua localização..." else "📍 Local definido (%.5f, %.5f)".format(c.first, c.second), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { picking = true }, enabled = c != null || vm.loc != null) { Text("Escolher outro local no mapa") }
            Text("🚫 É proibido anunciar venda de animais.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

            Button(
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val uri = photoUri
                    val loc = coords
                    if (uri == null) {
                        vm.message = "Adicione uma foto do animal."
                    } else if (loc == null) {
                        vm.message = "Defina a localização do pet."
                    } else {
                        vm.createPost(
                            uri,
                            mapOf(
                                "type" to type, "species" to species, "name" to name, "color" to color, "size" to size, "sex" to sex,
                                "description" to desc, "contactPhone" to phone, "address" to address,
                                "lat" to loc.first.toString(), "lng" to loc.second.toString()
                            )
                        ) { p -> if (p != null) onDone(p.id) }
                    }
                }
            ) { Text(if (vm.busy) "Publicando..." else "Publicar") }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (picking) {
        MapPickerDialog(initial = coords ?: vm.loc ?: (-15.78 to -47.93), onPick = { a, b -> coords = a to b; picking = false }, onDismiss = { picking = false })
    }
}
