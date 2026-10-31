@file:OptIn(ExperimentalMaterial3Api::class)

package com.voltapracasa.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File
import java.util.Locale

@Composable
fun Field(label: String, value: String, lines: Int = 1, kb: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), minLines = lines, maxLines = if (lines > 1) 6 else 1,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = kb)
    )
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        add("Perdi" to "LOST"); add("Avistei" to "SIGHTING"); add("Estou com ele" to "FOUND")
        if (vm.user?.role == "ONG" && vm.user?.ongStatus == "APPROVED") add("Adoção" to "ADOPTION")
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenBar("Novo anúncio", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FilterRow(types, type, horizontalPadding = 0.dp) { type = it }

            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(5.dp).fillMaxHeight().background(Labels.typeColor(type)))
                    Text(
                        when (type) {
                            "LOST" -> "Conte como seu pet é. Quanto mais detalhes, mais fácil de cruzar com avistamentos."
                            "SIGHTING" -> "Tire uma foto agora: o ponto aparece no mapa na hora, no local onde você está."
                            "FOUND" -> "Está cuidando de um pet que achou? Avise para o dono encontrar."
                            else -> "Adoção responsável. É proibido cobrar ou vender animais."
                        },
                        Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            val level = vm.user?.level ?: 1
            if (level < 3) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Nível $level de 3: a equipe confere a foto antes de o anúncio ir ao ar, para evitar fotos falsas. A partir do nível 3, a publicação é imediata.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Box(
                Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(model = photoUri, contentDescription = "Foto do pet", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PawMark(44.dp, MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Text("A foto ajuda a reconhecer o pet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Tirar foto", onClick = { takePhoto() }, modifier = Modifier.weight(1f))
                SecondaryButton("Galeria", onClick = { gallery.launch("image/*") }, modifier = Modifier.weight(1f))
            }

            SectionTitle("Sobre o pet")
            GroupLabel("Espécie")
            FilterRow(Labels.speciesList.map { Labels.species(it) to it }, species, horizontalPadding = 0.dp) { species = it }
            if (type != "SIGHTING") Field(if (type == "LOST") "Nome do pet" else "Nome (se souber)", name) { name = it }
            Field("Cor ou pelagem (ex.: caramelo, preto e branco)", color) { color = it }
            GroupLabel("Porte")
            FilterRow(listOf("Não sei" to "", "Pequeno" to "SMALL", "Médio" to "MEDIUM", "Grande" to "LARGE"), size, horizontalPadding = 0.dp) { size = it }
            GroupLabel("Sexo")
            FilterRow(listOf("Não sei" to "", "Macho" to "M", "Fêmea" to "F"), sex, horizontalPadding = 0.dp) { sex = it }
            Field("Detalhes (coleira, manchas, comportamento...)", desc, lines = 3) { desc = it }

            SectionTitle("Local")
            Text(
                "Quem se interessar fala com você pelo chat do app. Seu telefone não aparece para ninguém.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Field("Referência do local (rua, bairro)", address) { address = it }

            val c = coords
            SurfaceCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (c == null) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Place, contentDescription = null, tint = Brand.Lost)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (c == null) "Obtendo sua localização…" else "Local definido", style = MaterialTheme.typography.titleMedium)
                        if (c != null) {
                            Text(
                                String.format(Locale.US, "%.5f, %.5f", c.first, c.second),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = { picking = true }, enabled = c != null || vm.loc != null) { Text("Alterar no mapa") }
                }
            }

            Surface(shape = RoundedCornerShape(12.dp), color = Brand.Lost.copy(alpha = 0.12f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "É proibido anunciar venda de animais. Anúncios denunciados são removidos.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SafetyBanner()

            PrimaryButton(
                if (vm.busy) "Publicando…" else "Publicar anúncio",
                enabled = !vm.busy,
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
                                "description" to desc, "address" to address,
                                "lat" to loc.first.toString(), "lng" to loc.second.toString()
                            )
                        ) { p -> if (p != null) onDone(p.id) }
                    }
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (picking) {
        MapPickerDialog(initial = coords ?: vm.loc ?: (-15.78 to -47.93), onPick = { a, b -> coords = a to b; picking = false }, onDismiss = { picking = false })
    }
}
