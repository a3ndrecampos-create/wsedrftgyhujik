@file:OptIn(ExperimentalMaterial3Api::class)

package com.voltapracasa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val TYPE_FILTERS: List<Pair<String, String?>> = listOf(
    "Todos" to null, "Perdidos" to "LOST", "Avistados" to "SIGHTING", "Encontrados" to "FOUND", "Adoção" to "ADOPTION"
)
private val SPECIES_FILTERS: List<Pair<String, String?>> =
    listOf<Pair<String, String?>>("Todas as espécies" to null) + Labels.speciesList.map { Labels.species(it) to it }
private val RADIUS_FILTERS: List<Pair<String, Int>> = listOf("Sem limite" to 0, "5 km" to 5, "10 km" to 10, "25 km" to 25)

private data class NavTab(val label: String, val icon: ImageVector)

private val NAV_TABS = listOf(
    NavTab("Mapa", Icons.Filled.Place),
    NavTab("Pets", Icons.AutoMirrored.Filled.List),
    NavTab("Adoção", Icons.Filled.Favorite),
    NavTab("Conversas", ChatBubbleIcon),
    NavTab("Perfil", Icons.Filled.Person)
)

@Composable
fun HomeScreen(vm: AppVM, onOpen: (String) -> Unit, onNew: (String) -> Unit, onNeedLogin: () -> Unit, onChat: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var speciesFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val unread = vm.chats.sumOf { it.unread }
    LaunchedEffect(vm.user?.id) {
        while (vm.user != null) {
            vm.loadChats()
            delay(20000)
        }
    }

    val filtered = vm.posts.filter {
        (typeFilter == null || it.type == typeFilter) && (speciesFilter == null || it.species == speciesFilter)
    }

    Scaffold(
        topBar = {
            Column {
                BrandHeader(onRefresh = { vm.refresh(); vm.loadChats() })
                SafetyBanner(compact = true)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NAV_TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = {
                            if (i == 3 && unread > 0) {
                                BadgedBox(badge = { Badge { Text(unread.toString()) } }) { Icon(t.icon, contentDescription = null) }
                            } else {
                                Icon(t.icon, contentDescription = null)
                            }
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab < 3) ExtendedFloatingActionButton(
                onClick = { if (vm.user == null) onNeedLogin() else showMenu = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Publicar", style = MaterialTheme.typography.labelLarge) }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> Box(Modifier.fillMaxSize()) {
                    MapScreen(filtered, vm.loc, onOpen, topInset = 56.dp)
                    Surface(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), shadowElevation = 3.dp
                    ) {
                        Box(Modifier.padding(vertical = 6.dp)) { FilterRow(TYPE_FILTERS, typeFilter) { typeFilter = it } }
                    }
                }
                1 -> PostList(
                    posts = filtered, loading = vm.loading, onOpen = onOpen,
                    emptyTitle = "Nenhum anúncio por aqui",
                    emptyBody = "Tente ampliar o raio de busca ou mudar os filtros.",
                    header = {
                        val sub = when {
                            vm.loading && filtered.isEmpty() -> "Buscando anúncios…"
                            filtered.isEmpty() -> "Nenhum anúncio com esses filtros"
                            filtered.size == 1 -> "1 anúncio"
                            else -> "${filtered.size} anúncios"
                        }
                        Column {
                            Text("Perto de você", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineSmall)
                            Text(
                                sub, Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            FilterRow(TYPE_FILTERS, typeFilter) { typeFilter = it }
                            FilterRow(SPECIES_FILTERS, speciesFilter) { speciesFilter = it }
                            if (vm.loc != null) FilterRow(RADIUS_FILTERS, vm.radiusKm) { vm.setRadius(it) }
                        }
                    }
                )
                2 -> PostList(
                    posts = vm.posts.filter { it.type == "ADOPTION" && (speciesFilter == null || it.species == speciesFilter) },
                    loading = vm.loading, onOpen = onOpen,
                    emptyTitle = "Nenhum pet para adoção",
                    emptyBody = "Ainda não há pets para adoção na sua região. Volte em breve.",
                    header = {
                        Column {
                            Text("Adoção responsável", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Só ONGs e protetores aprovados publicam aqui. A venda de animais é proibida e pode ser denunciada em qualquer anúncio.",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            FilterRow(SPECIES_FILTERS, speciesFilter) { speciesFilter = it }
                        }
                    }
                )
                3 -> ChatsTab(vm, onChat)
                else -> ProfileTab(vm, onOpen)
            }
        }
    }

    if (showMenu) {
        val ong = vm.user?.role == "ONG"
        val options = listOf(
            Triple("LOST", "Perdi meu pet", "Peça ajuda da vizinhança para encontrar"),
            Triple("SIGHTING", "Vi um pet perdido na rua", "Tire uma foto e marque o local no mapa"),
            Triple("FOUND", "Estou com um pet que achei", "Avise para o dono te encontrar"),
            Triple("ADOPTION", "Pet para adoção", if (ong) "Divulgue um pet da sua ONG" else "Exclusivo para ONGs aprovadas")
        )
        AlertDialog(
            onDismissRequest = { showMenu = false },
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Fechar") } },
            title = { Text("O que você quer publicar?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { (t, title, subtitle) ->
                        PublishOption(Labels.typeColor(t), title, subtitle) {
                            showMenu = false
                            if (t == "ADOPTION" && vm.user?.ongStatus != "APPROVED") {
                                vm.message = if (ong) "Sua ONG ainda está aguardando aprovação." else "Adoção é exclusiva para ONGs aprovadas. Cadastre-se como ONG no Perfil."
                            } else onNew(t)
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun PublishOption(color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape, color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PostList(
    posts: List<Post>, loading: Boolean, emptyTitle: String, emptyBody: String,
    header: @Composable () -> Unit, onOpen: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 14.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { header() }
        if (loading && posts.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (posts.isEmpty()) {
            item { EmptyState(emptyTitle, emptyBody) }
        }
        items(posts, key = { it.id }) { p ->
            Box(Modifier.padding(horizontal = 16.dp)) { PostCard(p) { onOpen(p.id) } }
        }
    }
}
