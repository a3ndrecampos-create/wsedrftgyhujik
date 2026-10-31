package com.voltapracasa.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.config.Configuration
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data object Auth : Screen
    data class Detail(val id: String) : Screen
    data class New(val type: String) : Screen
    data class Chat(val id: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            load(applicationContext, applicationContext.getSharedPreferences("osm", MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        setContent {
            VoltaTheme {
                val vm: AppVM = viewModel()
                var showSplash by rememberSaveable { mutableStateOf(true) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Crossfade(targetState = showSplash, animationSpec = tween(400), label = "splash") { splash ->
                        if (splash) SplashScreen { showSplash = false } else AppRoot(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppVM) {
    val ctx = LocalContext.current
    val stack = remember { mutableStateListOf<Screen>() }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) getLocation(ctx, { a, b -> vm.setLocation(a, b) }, {})
    }
    LaunchedEffect(Unit) {
        permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    Box(Modifier.fillMaxSize()) {
        when (val s = stack.lastOrNull() ?: Screen.Home) {
            Screen.Home -> HomeScreen(
                vm = vm,
                onOpen = { stack.add(Screen.Detail(it)) },
                onNew = { stack.add(Screen.New(it)) },
                onNeedLogin = { vm.message = "Entre na sua conta para publicar."; stack.add(Screen.Auth) },
                onChat = { stack.add(Screen.Chat(it)) }
            )
            Screen.Auth -> {
                LaunchedEffect(vm.user) { if (vm.user != null) pop() }
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    ScreenBar("Entrar", onBack = { pop() })
                    Box(Modifier.weight(1f)) { AuthForm(vm) }
                }
            }
            is Screen.Detail -> DetailScreen(
                vm, s.id, onBack = { pop() },
                onOpen = { stack.add(Screen.Detail(it)) },
                onChat = { stack.add(Screen.Chat(it)) }
            )
            is Screen.Chat -> ChatScreen(vm, s.id, onBack = { pop() }, onOpenPost = { stack.add(Screen.Detail(it)) })
            is Screen.New -> NewPostScreen(vm, s.type, onBack = { pop() }, onDone = { id ->
                stack.clear()
                stack.add(Screen.Detail(id))
            })
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
}
