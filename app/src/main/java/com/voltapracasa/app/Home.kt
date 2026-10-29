@file:OptIn(ExperimentalMaterial3Api::class)

package com.voltapracasa.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TYPE_FILTERS: List<Pair<String, String?>> = listOf(
    "Todos" to null, "Perdidos" to "LOST", "Avistados" to "SIGHTING", "Encontrados" to "FOUND", "Adoção" to "ADOPTION"
)
private val SPECIES_FILTERS: List<Pair<String, String?>> =
    listOf<Pair<String, String?>>("Todas as espécies" to null) + Labels.speciesList.map { Labels.species(it) to it }
private val RADIUS_FILTERS: List<Pair<String, Int>> = listOf("Sem limite" to 0, "5 km" to 5, "10 km" to 10, "25 km" to 25)

@Composable
fun HomeScreen(vm: AppVM, onOpen: (String) -> Unit, onNew: (String) -> Unit, onNeedLogin: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var speciesFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = vm.posts.filter {
        (typeFilter == null || it.type == typeFilter) && (speciesFilter == null || it.species == speciesFilter)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("🗺️" to "Mapa", "🐾" to "Pets", "🏠" to "Adoção", "👤" to "Perfil").forEachIndexed { i, (emoji, label) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(emoji, fontSize = 20.sp) }, label = { Text(label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab < 3) ExtendedFloatingActionButton(onClick = {
                if (vm.user == null) onNeedLogin() else showMenu = true
            }) { Text("＋ Publicar") }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> Column {
                    FilterRow(TYPE_FILTERS, typeFilter) { typeFilter = it }
                    Box(Modifier.weight(1f)) { MapScreen(filtered, vm.loc, onOpen) }
                }
                1 -> PostList(
                    posts = filtered, loading = vm.loading, onOpen = onOpen,
                    empty = "Nenhum pet por aqui ainda. Tente ampliar o raio de busca.",
                    header = {
                        Column {
                            FilterRow(TYPE_FILTERS, typeFilter) { typeFilter = it }
                            FilterRow(SPECIES_FILTERS, speciesFilter) { speciesFilter = it }
                            if (vm.loc != null) FilterRow(RADIUS_FILTERS, vm.radiusKm) { vm.setRadius(it) }
                            TextButton(onClick = { vm.refresh() }) { Text("↻ Atualizar") }
                        }
                    }
                )
                2 -> PostList(
                    posts = vm.posts.filter { it.type == "ADOPTION" && (speciesFilter == null || it.species == speciesFilter) },
                    loading = vm.loading, onOpen = onOpen,
                    empty = "Ainda não há pets para adoção na sua região.",
                    header = {
                        Column {
                            Card(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text(
                                    "🏠 Adoção responsável. Só ONGs e protetores aprovados publicam aqui. " +
                                        "A venda de animais é proibida e pode ser denunciada em qualquer anúncio.",
                                    Modifier.padding(12.dp), fontSize = 13.sp
                                )
                            }
                            FilterRow(SPECIES_FILTERS, speciesFilter) { speciesFilter = it }
                        }
                    }
                )
                else -> ProfileTab(vm, onOpen)
            }
        }
    }

    if (showMenu) {
        val ong = vm.user?.role == "ONG"
        AlertDialog(
            onDismissRequest = { showMenu = false },
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Fechar") } },
            title = { Text("O que você quer publicar?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "🔍  Perdi meu pet" to "LOST",
                        "📸  Vi um pet perdido na rua" to "SIGHTING",
                        "🤝  Estou com um pet que achei" to "FOUND",
                        (if (ong) "🏠  Divulgar pet para adoção (ONG)" else "🏠  Adoção (somente ONGs)") to "ADOPTION"
                    ).forEach { (label, t) ->
                        OutlinedButton(onClick = {
                            showMenu = false
                            if (t == "ADOPTION" && vm.user?.ongStatus != "APPROVED") {
                                vm.message = if (ong) "Sua ONG ainda está aguardando aprovação." else "Adoção é exclusiva para ONGs aprovadas. Cadastre-se como ONG no Perfil."
                            } else onNew(t)
                        }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                }
            }
        )
    }
}

@Composable
fun PostList(posts: List<Post>, loading: Boolean, empty: String, header: @Composable () -> Unit, onOpen: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { header() }
        if (loading && posts.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (posts.isEmpty()) {
            item { Text(empty, Modifier.padding(24.dp)) }
        }
        items(posts, key = { it.id }) { PostCard(it) { onOpen(it.id) } }
    }
}
