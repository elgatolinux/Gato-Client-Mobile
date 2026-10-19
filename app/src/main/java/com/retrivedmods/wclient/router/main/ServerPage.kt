package com.retrivedmods.wclient.router.main

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retrivedmods.wclient.R
import com.retrivedmods.wclient.ui.component.WFloatingActionButton
import com.retrivedmods.wclient.ui.theme.WColors
import com.retrivedmods.wclient.util.LocalSnackbarHostState
import com.retrivedmods.wclient.util.SnackbarHostStateScope
import com.retrivedmods.wclient.viewmodel.MainScreenViewModel
import kotlinx.coroutines.launch

data class ServerInfo(
    val name: String,
    val iconRes: Int,
    val address: String,
    val port: Int,
    val isCustom: Boolean = false
)

val defaultServers = listOf(
    ServerInfo(
        name = "2b2tpe (Anarchy HvH)",
        iconRes = R.drawable.swords_24px,
        address = "2b2tpe.org",
        port = 19132
    ),
    ServerInfo(
        name = "Lifeboat",
        iconRes = R.drawable.lifeboat_icon,
        address = "play.lbsg.net",
        port = 19132
    ),
    ServerInfo(
        name = "CubeCraft",
        iconRes = R.drawable.cubecraft_icon,
        address = "play.cubecraft.net",
        port = 19132
    ),
    ServerInfo(
        name = "NetherGames",
        iconRes = R.drawable.nethergames,
        address = "play.nethergames.org",
        port = 19132
    ),
    ServerInfo(
        name = "DonutSMP",
        iconRes = R.drawable.donutsmp_icon,
        address = "donutsmp.net",
        port = 19132
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerPageContent() {
    SnackbarHostStateScope {
        val context = LocalContext.current
        val mainScreenViewModel: MainScreenViewModel = viewModel()
        val captureModeModel by mainScreenViewModel.captureModeModel.collectAsStateWithLifecycle()
        val snackbarHostState = LocalSnackbarHostState.current
        val scope = rememberCoroutineScope()

        val prefs = remember { context.getSharedPreferences("custom_servers", Context.MODE_PRIVATE) }
        var customServers by remember { mutableStateOf(loadCustomServers(prefs)) }
        var showAddDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Servers",
                                style = MaterialTheme.typography.headlineMedium,
                                color = WColors.OnSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = WColors.Background,
                        titleContentColor = WColors.OnSurface
                    )
                )
            },
            floatingActionButton = {
                WFloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = WColors.Primary,
                    contentColor = WColors.OnPrimary
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Server")
                }
            },
            bottomBar = {
                SnackbarHost(
                    snackbarHostState,
                    modifier = Modifier.animateContentSize()
                )
            },
            containerColor = WColors.Background
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val allServers = defaultServers + customServers
                allServers.forEach { server ->
                    val isSelected = captureModeModel.serverHostName.equals(server.address, ignoreCase = true) &&
                            captureModeModel.serverPort == server.port
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                mainScreenViewModel.selectCaptureModeModel(
                                    captureModeModel.copy(
                                        serverHostName = server.address,
                                        serverPort = server.port
                                    ).withAutoDetectedServerConfig()
                                )
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar("Selected ${server.name} (${server.address}:${server.port})")
                                }
                            }
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        2.dp,
                                        WColors.Primary,
                                        MaterialTheme.shapes.medium
                                    )
                                } else Modifier
                            ),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) WColors.SurfaceVariant else WColors.Surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (server.isCustom) {
                                Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = WColors.Primary
                                )
                            } else {
                                Icon(
                                    painter = painterResource(server.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = if (server.iconRes == R.drawable.swords_24px) WColors.Primary else Color.Unspecified
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = server.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WColors.OnSurface
                                )
                                Text(
                                    text = "${server.address}:${server.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WColors.OnSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = WColors.Primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            if (server.isCustom) {
                                IconButton(
                                    onClick = {
                                        val updated = customServers.filterNot {
                                            it.name == server.name && it.address == server.address && it.port == server.port
                                        }
                                        customServers = updated
                                        saveCustomServers(prefs, updated)
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = WColors.Error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showAddDialog) {
                var newName by remember { mutableStateOf("") }
                var newHost by remember { mutableStateOf("") }
                var newPort by remember { mutableStateOf("19132") }

                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("Add Custom Server") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("Server Name") },
                                placeholder = { Text("My HvH Server") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = newHost,
                                onValueChange = { newHost = it },
                                label = { Text("Server Address / IP") },
                                placeholder = { Text("play.example.net") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = newPort,
                                onValueChange = { newPort = it },
                                label = { Text("Port") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val host = newHost.trim()
                                if (host.isNotEmpty()) {
                                    val port = newPort.toIntOrNull()?.coerceIn(1, 65535) ?: 19132
                                    val name = newName.trim().ifEmpty { host }
                                    val added = ServerInfo(
                                        name = name,
                                        iconRes = R.drawable.public_24px,
                                        address = host,
                                        port = port,
                                        isCustom = true
                                    )
                                    val updated = customServers + added
                                    customServers = updated
                                    saveCustomServers(prefs, updated)
                                    showAddDialog = false
                                }
                            }
                        ) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

private fun loadCustomServers(prefs: android.content.SharedPreferences): List<ServerInfo> {
    val raw = prefs.getString("list", "") ?: ""
    if (raw.isBlank()) return emptyList()
    return raw.split("\n").mapNotNull { line ->
        val parts = line.split("|")
        if (parts.size >= 3) {
            ServerInfo(
                name = parts[0],
                iconRes = R.drawable.public_24px,
                address = parts[1],
                port = parts[2].toIntOrNull() ?: 19132,
                isCustom = true
            )
        } else null
    }
}

private fun saveCustomServers(prefs: android.content.SharedPreferences, list: List<ServerInfo>) {
    val serialized = list.joinToString("\n") { "${it.name}|${it.address}|${it.port}" }
    prefs.edit { putString("list", serialized) }
}
