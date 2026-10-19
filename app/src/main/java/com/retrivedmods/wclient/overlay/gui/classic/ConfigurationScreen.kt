package com.retrivedmods.wclient.overlay.gui.classic

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrivedmods.wclient.application.AppContext
import com.retrivedmods.wclient.game.ModuleManager
import com.retrivedmods.wclient.game.rememberGuiFontFamily
import com.retrivedmods.wclient.overlay.ColorPreset
import com.retrivedmods.wclient.overlay.FloatingButtonShape
import com.retrivedmods.wclient.overlay.GUITheme
import com.retrivedmods.wclient.overlay.GuiSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

private enum class ConfigTab(val label: String) {
    GUI_STYLE("🎨 GUI & Theme"),
    CONTROLS("🔘 Floating & HUD"),
    PROFILES("💾 Profiles")
}

@Composable
fun ConfigurationScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()
    var selectedTab by remember { mutableStateOf(ConfigTab.GUI_STYLE) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConfigTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                Surface(
                    onClick = { selectedTab = tab },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) palette.accent else palette.cardExpandedBg,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            color = if (isSelected) Color.White else palette.textPrimary,
                            fontSize = 11.sp,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                ConfigTab.GUI_STYLE -> GuiStyleSection()
                ConfigTab.CONTROLS -> FloatingControlsSection(snackbarHostState)
                ConfigTab.PROFILES -> ProfilesSection(snackbarHostState)
            }
        }
    }
}

@Composable
private fun GuiStyleSection() {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    // 1. ClickGUI Design Selector
    SectionCard(title = "ClickGUI Design Layout") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GUITheme.entries.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { theme ->
                        val selected = GuiSettingsManager.guiTheme == theme
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) palette.accent.copy(alpha = 0.18f) else palette.cardBg)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) palette.accent else palette.border,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { GuiSettingsManager.updateGuiTheme(theme) }
                                .padding(10.dp)
                        ) {
                            Text(
                                text = theme.displayName,
                                color = if (selected) palette.accent else palette.textPrimary,
                                fontSize = 12.sp,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = theme.description,
                                color = palette.textSecondary,
                                fontSize = 10.sp,
                                fontFamily = fontFamily,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }

    // 2. Color Palette & Custom RGB
    SectionCard(title = "Color Preset & Custom RGB") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColorPreset.entries.forEach { preset ->
                val selected = GuiSettingsManager.colorPreset == preset
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) palette.accent else palette.cardBg)
                        .border(1.dp, if (selected) palette.accent else palette.border, RoundedCornerShape(50))
                        .clickable { GuiSettingsManager.updateColorPreset(preset) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(preset.accent)
                            .border(1.dp, Color.White, CircleShape)
                    )
                    Text(
                        text = preset.displayName,
                        color = if (selected) Color.White else palette.textPrimary,
                        fontSize = 11.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (GuiSettingsManager.colorPreset == ColorPreset.CUSTOM) {
            Spacer(Modifier.height(6.dp))
            ConfigToggleRow(
                label = "Custom Dark Mode Surface",
                checked = GuiSettingsManager.customDarkMode,
                onCheckedChange = { GuiSettingsManager.updateCustomRgb(dark = it) }
            )
            ConfigIntSlider(
                label = "Accent Red",
                value = GuiSettingsManager.customAccentR,
                range = 0..255,
                onValueChange = { GuiSettingsManager.updateCustomRgb(r = it) }
            )
            ConfigIntSlider(
                label = "Accent Green",
                value = GuiSettingsManager.customAccentG,
                range = 0..255,
                onValueChange = { GuiSettingsManager.updateCustomRgb(g = it) }
            )
            ConfigIntSlider(
                label = "Accent Blue",
                value = GuiSettingsManager.customAccentB,
                range = 0..255,
                onValueChange = { GuiSettingsManager.updateCustomRgb(b = it) }
            )
        }
    }

    // 3. Window Geometry, Blur & Font
    SectionCard(title = "Window Dimensions, Blur & Typography") {
        ConfigIntSlider(
            label = "GUI Width (dp)",
            value = GuiSettingsManager.guiWidthDp,
            range = 460..820,
            onValueChange = { GuiSettingsManager.updateWindowStyle(widthDp = it) }
        )
        ConfigIntSlider(
            label = "GUI Height (dp)",
            value = GuiSettingsManager.guiHeightDp,
            range = 260..480,
            onValueChange = { GuiSettingsManager.updateWindowStyle(heightDp = it) }
        )
        ConfigFloatSlider(
            label = "Window Opacity",
            value = GuiSettingsManager.guiOpacity,
            range = 0.50f..1.0f,
            onValueChange = { GuiSettingsManager.updateWindowStyle(opacity = it) }
        )
        ConfigIntSlider(
            label = "Corner Radius (dp)",
            value = GuiSettingsManager.cornerRadiusDp,
            range = 4..32,
            onValueChange = { GuiSettingsManager.updateWindowStyle(cornerDp = it) }
        )
        ConfigFloatSlider(
            label = "Backdrop Dim Amount",
            value = GuiSettingsManager.dimAmount,
            range = 0.0f..0.85f,
            onValueChange = { GuiSettingsManager.updateWindowStyle(dim = it) }
        )
        ConfigToggleRow(
            label = "Liquid Glass Blur (Android 12+)",
            checked = GuiSettingsManager.enableBlur,
            onCheckedChange = { GuiSettingsManager.updateWindowStyle(blur = it) }
        )
        if (GuiSettingsManager.enableBlur) {
            ConfigIntSlider(
                label = "Blur Radius (px)",
                value = GuiSettingsManager.blurRadius,
                range = 5..80,
                onValueChange = { GuiSettingsManager.updateWindowStyle(blurRad = it) }
            )
        }
        ConfigToggleRow(
            label = "Use Minecraft Pixel Font",
            checked = GuiSettingsManager.useMinecraftFont,
            onCheckedChange = { GuiSettingsManager.updateWindowStyle(mcFont = it) }
        )
    }
}

@Composable
private fun FloatingControlsSection(snackbarHostState: SnackbarHostState) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()
    val scope = rememberCoroutineScope()

    SectionCard(title = "Floating Gato Button") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingButtonShape.entries.forEach { shape ->
                val selected = GuiSettingsManager.buttonShape == shape
                ElevatedFilterChip(
                    selected = selected,
                    onClick = { GuiSettingsManager.updateFloatingControls(shape = shape) },
                    label = { Text(shape.displayName, fontFamily = fontFamily, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = palette.cardBg,
                        selectedContainerColor = palette.accent,
                        labelColor = palette.textSecondary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        ConfigIntSlider(
            label = "Button Size (dp)",
            value = GuiSettingsManager.buttonSizeDp,
            range = 36..76,
            onValueChange = { GuiSettingsManager.updateFloatingControls(btnSize = it) }
        )
        ConfigFloatSlider(
            label = "Button Opacity",
            value = GuiSettingsManager.overlayOpacity,
            range = 0.15f..1.0f,
            onValueChange = { GuiSettingsManager.updateFloatingControls(btnOpacity = it) }
        )
        ConfigToggleRow(
            label = "Show Active Module Count Badge",
            checked = GuiSettingsManager.showActiveBadge,
            onCheckedChange = { GuiSettingsManager.updateFloatingControls(badge = it) }
        )
        ConfigToggleRow(
            label = "Accent Border Glow",
            checked = GuiSettingsManager.buttonBorderGlow,
            onCheckedChange = { GuiSettingsManager.updateFloatingControls(glow = it) }
        )
        ConfigToggleRow(
            label = "Lock Floating Button Position",
            checked = GuiSettingsManager.lockButtonDrag,
            onCheckedChange = { GuiSettingsManager.updateFloatingControls(lockBtn = it) }
        )
    }

    SectionCard(title = "Floating Module Shortcuts") {
        ConfigFloatSlider(
            label = "Shortcuts Scale",
            value = GuiSettingsManager.shortcutScale,
            range = 0.70f..1.40f,
            onValueChange = { GuiSettingsManager.updateFloatingControls(shortScale = it) }
        )
        ConfigFloatSlider(
            label = "Shortcuts Opacity",
            value = GuiSettingsManager.shortcutOpacity,
            range = 0.15f..1.0f,
            onValueChange = { GuiSettingsManager.updateFloatingControls(shortOpacity = it) }
        )
        ConfigToggleRow(
            label = "Lock Shortcuts Position (Anti-Misclick)",
            checked = GuiSettingsManager.lockShortcutsDrag,
            onCheckedChange = { GuiSettingsManager.updateFloatingControls(lockShort = it) }
        )
    }

    SectionCard(title = "Emergency / Quick Actions") {
        Button(
            onClick = {
                var count = 0
                ModuleManager.modules.forEach {
                    if (it.isEnabled && !it.private) {
                        it.isEnabled = false
                        count++
                    }
                }
                scope.launch {
                    snackbarHostState.showSnackbar("🛑 Panic: Disabled $count active modules")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF3B5C),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("PANIC — Disable All Active Modules", fontFamily = fontFamily, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfilesSection(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    var profileName by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }

    val savedProfiles = remember(refreshKey) {
        val list = mutableListOf<File>()
        runCatching {
            val internalDir = AppContext.instance.filesDir.resolve("configs")
            internalDir.listFiles { f -> f.extension == "json" }?.let { list.addAll(it) }
        }
        runCatching {
            ModuleManager.getWClientConfigsDirectory()
                ?.listFiles { f -> f.extension == "json" }
                ?.let { extFiles ->
                    extFiles.forEach { ef ->
                        if (list.none { it.absolutePath == ef.absolutePath }) list.add(ef)
                    }
                }
        }
        list.sortedByDescending { it.lastModified() }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val ok = ModuleManager.importConfigFromFile(context, it)
            ModuleManager.saveConfig()
            refreshKey++
            coroutineScope.launch {
                snackbarHostState.showSnackbar(if (ok) "✅ Config imported!" else "❌ Failed to import config")
            }
        }
    }

    SectionCard(title = "Save or Import Profile") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                placeholder = { Text("Profile name (e.g. 2b2t_hvh)", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary
                )
            )
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val cleanName = profileName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
                            .ifEmpty { generateUniqueFileName().removeSuffix(".json") }
                        val fileName = "$cleanName.json"
                        val internalDir = AppContext.instance.filesDir.resolve("configs").apply { mkdirs() }
                        val content = ModuleManager.exportConfig()
                        runCatching { File(internalDir, fileName).writeText(content) }
                        runCatching {
                            ModuleManager.getWClientConfigsDirectory()?.let { File(it, fileName).writeText(content) }
                        }
                        withContext(Dispatchers.Main) {
                            profileName = ""
                            refreshKey++
                            snackbarHostState.showSnackbar("✅ Saved profile: $fileName")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save", fontFamily = fontFamily)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { filePickerLauncher.launch("application/json") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Rounded.Upload, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import File", color = palette.textPrimary, fontSize = 12.sp, fontFamily = fontFamily)
            }
            OutlinedButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val fileName = generateUniqueFileName()
                        val ok = exportConfigToWClientFolder(fileName)
                        withContext(Dispatchers.Main) {
                            refreshKey++
                            snackbarHostState.showSnackbar(
                                if (ok) "✅ Exported $fileName" else "❌ Export failed"
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Rounded.SaveAlt, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Quick Export", color = palette.textPrimary, fontSize = 12.sp, fontFamily = fontFamily)
            }
        }
    }

    SectionCard(title = "Saved Profiles (${savedProfiles.size})") {
        if (savedProfiles.isEmpty()) {
            Text(
                text = "No saved profiles yet. Save one above!",
                color = palette.textSecondary,
                fontSize = 12.sp,
                fontFamily = fontFamily
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                savedProfiles.forEach { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.cardBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.nameWithoutExtension,
                                color = palette.textPrimary,
                                fontSize = 12.sp,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = file.parentFile?.name ?: "",
                                color = palette.textSecondary,
                                fontSize = 10.sp,
                                fontFamily = fontFamily
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        ModuleManager.importConfig(file.readText())
                                        ModuleManager.saveConfig()
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("✅ Loaded ${file.nameWithoutExtension}")
                                        }
                                    }.onFailure {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("❌ Error loading ${file.name}")
                                        }
                                    }
                                }
                            ) {
                                Text("Load", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            if (file.name != "UserConfig.json") {
                                IconButton(
                                    onClick = {
                                        runCatching { file.delete() }
                                        refreshKey++
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    SectionCard(title = "Credits") {
        Text("ESC5P5 • Gato • Master123", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = fontFamily)
        Text("Gato Client Mobile — 2b2tpe HvH Edition", color = palette.textSecondary, fontSize = 11.sp, fontFamily = fontFamily)
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.cardExpandedBg)
            .border(1.dp, palette.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            color = palette.accent,
            fontSize = 13.sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
private fun ConfigToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = palette.textPrimary, fontSize = 12.sp, fontFamily = fontFamily)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.accent,
                checkedTrackColor = palette.accent.copy(alpha = 0.35f),
                uncheckedThumbColor = palette.textSecondary,
                uncheckedTrackColor = palette.cardBg
            )
        )
    }
}

@Composable
private fun ConfigIntSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = palette.textSecondary, fontSize = 11.sp, fontFamily = fontFamily)
            Text(text = "$value", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.border
            )
        )
    }
}

@Composable
private fun ConfigFloatSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = palette.textSecondary, fontSize = 11.sp, fontFamily = fontFamily)
            Text(text = String.format("%.2f", value), color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily)
        }
        Slider(
            value = value,
            onValueChange = {
                val rounded = ((it * 100.0).roundToInt() / 100.0).toFloat()
                onValueChange(rounded)
            },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.border
            )
        )
    }
}

private fun generateUniqueFileName(): String {
    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    val timestamp = dateFormat.format(Date())
    return "config_$timestamp.json"
}

private fun exportConfigToWClientFolder(fileName: String): Boolean {
    return try {
        val configsDir = ModuleManager.getWClientConfigsDirectory() ?: return false
        val file = File(configsDir, fileName)
        file.writeText(ModuleManager.exportConfig())
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
