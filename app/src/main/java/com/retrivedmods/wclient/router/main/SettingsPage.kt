package com.retrivedmods.wclient.router.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrivedmods.wclient.R
import com.retrivedmods.wclient.game.ModuleManager
import com.retrivedmods.wclient.overlay.ColorPreset
import com.retrivedmods.wclient.overlay.FloatingButtonShape
import com.retrivedmods.wclient.overlay.GUITheme
import com.retrivedmods.wclient.overlay.GuiSettingsManager
import com.retrivedmods.wclient.ui.component.WGlassCard
import com.retrivedmods.wclient.ui.theme.WColors
import com.retrivedmods.wclient.util.LocalSnackbarHostState
import com.retrivedmods.wclient.util.SnackbarHostStateScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageContent() {
    SnackbarHostStateScope {
        val context = LocalContext.current
        GuiSettingsManager.ensureLoaded(context)

        val snackbarHostState = LocalSnackbarHostState.current
        val scope = rememberCoroutineScope()

        var showFileNameDialog by rememberSaveable { mutableStateOf(false) }
        var configFileName by rememberSaveable { mutableStateOf("") }
        var isExporting by remember { mutableStateOf(false) }

        val importConfigPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                val ok = ModuleManager.importConfigFromFile(context, it)
                ModuleManager.saveConfig()
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) "Config imported successfully" else "Failed to import config"
                    )
                }
            }
        }

        suspend fun exportConfig(fileName: String): Result<String> = withContext(Dispatchers.IO) {
            try {
                val configsDir = ModuleManager.getWClientConfigsDirectory()
                    ?: return@withContext Result.failure(Exception("Failed to create WClient directory"))

                val finalFileName = if (fileName.isBlank()) {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                    "config_$timestamp"
                } else {
                    fileName
                }

                val configFile = File(configsDir, "$finalFileName.json")
                val success = ModuleManager.exportConfigToFile(context, configFile.absolutePath)

                if (success) {
                    Result.success(configFile.absolutePath)
                } else {
                    Result.failure(Exception("Failed to export configuration"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.settings),
                            style = MaterialTheme.typography.headlineMedium,
                            color = WColors.OnSurface
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = WColors.Background,
                        titleContentColor = WColors.OnSurface
                    )
                )
            },
            bottomBar = { SnackbarHost(LocalSnackbarHostState.current) },
            containerColor = WColors.Background
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. ClickGUI Design Selector Card
                WGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = WColors.Primary,
                    glowIntensity = 0.25f
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.ViewCarousel, null, tint = WColors.Primary, modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text("ClickGUI Design", style = MaterialTheme.typography.titleMedium, color = WColors.OnSurface)
                                Text(
                                    "Choose between 4 interactive ClickGUI layouts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WColors.OnSurfaceVariant
                                )
                            }
                        }

                        GUITheme.entries.chunked(2).forEach { rowThemes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowThemes.forEach { theme ->
                                    val selected = GuiSettingsManager.guiTheme == theme
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (selected) WColors.Primary.copy(alpha = 0.15f)
                                                else WColors.SurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) WColors.Primary else WColors.Border,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { GuiSettingsManager.updateGuiTheme(theme) }
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = theme.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (selected) WColors.Primary else WColors.OnSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = theme.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = WColors.OnSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Color Presets & Custom RGB Card
                WGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = WColors.Secondary,
                    glowIntensity = 0.25f
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Palette, null, tint = WColors.Secondary, modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text("ClickGUI Color Palette", style = MaterialTheme.typography.titleMedium, color = WColors.OnSurface)
                                Text(
                                    "Select a preset theme or customize your own RGB palette",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WColors.OnSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ColorPreset.entries.forEach { preset ->
                                val selected = GuiSettingsManager.colorPreset == preset
                                FilterChip(
                                    selected = selected,
                                    onClick = { GuiSettingsManager.updateColorPreset(preset) },
                                    label = { Text(preset.displayName) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(preset.accent)
                                        )
                                    }
                                )
                            }
                        }

                        if (GuiSettingsManager.colorPreset == ColorPreset.CUSTOM) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Dark Surface Mode", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = GuiSettingsManager.customDarkMode,
                                    onCheckedChange = { GuiSettingsManager.updateCustomRgb(dark = it) }
                                )
                            }
                            Text("Red: ${GuiSettingsManager.customAccentR}", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = GuiSettingsManager.customAccentR.toFloat(),
                                onValueChange = { GuiSettingsManager.updateCustomRgb(r = it.roundToInt()) },
                                valueRange = 0f..255f
                            )
                            Text("Green: ${GuiSettingsManager.customAccentG}", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = GuiSettingsManager.customAccentG.toFloat(),
                                onValueChange = { GuiSettingsManager.updateCustomRgb(g = it.roundToInt()) },
                                valueRange = 0f..255f
                            )
                            Text("Blue: ${GuiSettingsManager.customAccentB}", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = GuiSettingsManager.customAccentB.toFloat(),
                                onValueChange = { GuiSettingsManager.updateCustomRgb(b = it.roundToInt()) },
                                valueRange = 0f..255f
                            )
                        }
                    }
                }

                // 3. Floating Button & Opacity Settings Card
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Opacity, null, modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Overlay Button, Blur & Shortcuts",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Customize floating controls, opacity, lock & Liquid Glass blur",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FloatingButtonShape.entries.forEach { shape ->
                                FilterChip(
                                    selected = GuiSettingsManager.buttonShape == shape,
                                    onClick = { GuiSettingsManager.updateFloatingControls(shape = shape) },
                                    label = { Text(shape.displayName) }
                                )
                            }
                        }

                        Text("${stringResource(R.string.overlay_opacity)}: ${(GuiSettingsManager.overlayOpacity * 100).roundToInt()}%")
                        Slider(
                            value = GuiSettingsManager.overlayOpacity,
                            onValueChange = { GuiSettingsManager.updateFloatingControls(btnOpacity = it) },
                            valueRange = 0.15f..1f
                        )

                        Text("${stringResource(R.string.shortcut_opacity)}: ${(GuiSettingsManager.shortcutOpacity * 100).roundToInt()}%")
                        Slider(
                            value = GuiSettingsManager.shortcutOpacity,
                            onValueChange = { GuiSettingsManager.updateFloatingControls(shortOpacity = it) },
                            valueRange = 0.15f..1f
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Liquid Glass Blur (Android 12+)", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = GuiSettingsManager.enableBlur,
                                onCheckedChange = { GuiSettingsManager.updateWindowStyle(blur = it) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Lock Shortcut Positions (Anti-Misclick)", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = GuiSettingsManager.lockShortcutsDrag,
                                onCheckedChange = { GuiSettingsManager.updateFloatingControls(lockShort = it) }
                            )
                        }
                    }
                }

                // 4. Config Management Card
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.SaveAlt, null, modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Config Management", style = MaterialTheme.typography.bodyLarge)
                                Text("Save and load module configurations", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        FilledTonalButton(
                            onClick = { importConfigPicker.launch("application/json") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Upload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import Config")
                        }

                        FilledTonalButton(
                            onClick = { showFileNameDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.SaveAlt, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isExporting) "Exporting..." else "Export Config")
                        }
                    }
                }

                if (showFileNameDialog) {
                    BasicAlertDialog(
                        onDismissRequest = {
                            if (!isExporting) {
                                showFileNameDialog = false
                                configFileName = ""
                            }
                        },
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = AlertDialogDefaults.shape,
                            tonalElevation = AlertDialogDefaults.TonalElevation
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "Export Configuration",
                                    style = MaterialTheme.typography.headlineSmall
                                )

                                Text(
                                    "Config will be saved to: Documents/WClient/configs/",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = configFileName,
                                    onValueChange = { configFileName = it },
                                    label = { Text("Configuration Name (Optional)") },
                                    placeholder = { Text("my-config") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isExporting,
                                    supportingText = {
                                        Text(
                                            if (configFileName.isBlank())
                                                "Leave empty to use timestamp"
                                            else
                                                "File: $configFileName.json"
                                        )
                                    }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            showFileNameDialog = false
                                            configFileName = ""
                                        },
                                        enabled = !isExporting
                                    ) {
                                        Text("Cancel")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            isExporting = true
                                            scope.launch {
                                                val result = exportConfig(configFileName)
                                                isExporting = false

                                                result.onSuccess { filePath ->
                                                    snackbarHostState.showSnackbar(
                                                        message = "Config saved successfully!\nLocation: $filePath",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                }.onFailure { error ->
                                                    snackbarHostState.showSnackbar(
                                                        message = "Export failed: ${error.message}",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                }

                                                showFileNameDialog = false
                                                configFileName = ""
                                            }
                                        },
                                        enabled = !isExporting
                                    ) {
                                        Text(if (isExporting) "Saving..." else "Export")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
