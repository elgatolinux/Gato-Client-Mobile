package com.retrivedmods.wclient.overlay.gui.classic

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrivedmods.wclient.R
import com.retrivedmods.wclient.game.*
import com.retrivedmods.wclient.overlay.GUITheme
import com.retrivedmods.wclient.overlay.GuiPalette
import com.retrivedmods.wclient.overlay.GuiSettingsManager
import com.retrivedmods.wclient.overlay.OverlayManager
import com.retrivedmods.wclient.overlay.OverlayWindow
import com.retrivedmods.wclient.util.translatedSelf

class OverlayClickGUI : OverlayWindow() {

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            if (GuiSettingsManager.enableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = GuiSettingsManager.blurRadius
            }
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            dimAmount = GuiSettingsManager.dimAmount
            windowAnimations = android.R.style.Animation_Dialog
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    private var selectedModuleCategory by mutableStateOf(ModuleCategory.Combat)
    private var searchQuery by mutableStateOf("")
    private var onlyFavorites by mutableStateOf(false)
    private var onlyActive by mutableStateOf(false)

    @Composable
    override fun Content() {
        val context = LocalContext.current
        GuiSettingsManager.ensureLoaded(context)

        val snackbarHostState = remember { SnackbarHostState() }
        val palette = GuiSettingsManager.currentPalette()
        val theme = GuiSettingsManager.guiTheme
        val enableBlur = GuiSettingsManager.enableBlur
        val blurRad = GuiSettingsManager.blurRadius
        val dim = GuiSettingsManager.dimAmount

        LaunchedEffect(enableBlur, blurRad, dim) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (enableBlur) {
                        _layoutParams.flags = _layoutParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                        _layoutParams.blurBehindRadius = blurRad
                    } else {
                        _layoutParams.flags = _layoutParams.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                        _layoutParams.blurBehindRadius = 0
                    }
                }
                _layoutParams.dimAmount = dim
                windowManager.updateViewLayout(composeView, _layoutParams)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (dim * 0.85f).coerceIn(0f, 0.85f)))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    OverlayManager.dismissOverlayWindow(this)
                },
            contentAlignment = Alignment.Center
        ) {
            when (theme) {
                GUITheme.CLASSIC -> ClassicGuiLayout(palette, snackbarHostState)
                GUITheme.PANELS -> PanelsDropdownGuiLayout(palette, snackbarHostState)
                GUITheme.DASHBOARD -> SplitDashboardGuiLayout(palette, snackbarHostState)
                GUITheme.COMPACT -> CompactDockGuiLayout(palette, snackbarHostState)
            }
        }
    }

    // =========================================================================
    // 1. CLASSIC GATO LAYOUT (Enhanced with Search, Favorites & Dynamic Theme)
    // =========================================================================
    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    private fun ClassicGuiLayout(
        palette: GuiPalette,
        snackbarHostState: SnackbarHostState
    ) {
        val context = LocalContext.current
        val cornerShape = RoundedCornerShape(GuiSettingsManager.cornerRadiusDp.dp)

        Box(
            modifier = Modifier
                .width(GuiSettingsManager.guiWidthDp.dp)
                .height(GuiSettingsManager.guiHeightDp.dp)
                .clip(cornerShape)
                .background(
                    Brush.verticalGradient(listOf(palette.windowBgTop, palette.windowBgBottom)),
                    cornerShape
                )
                .border(1.5.dp, palette.border, cornerShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopHeaderBar(
                    palette = palette,
                    onDiscord = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/N2Gejr8Fbp")))
                    },
                    onCycleLayout = {
                        val all = GUITheme.entries
                        val next = all[(all.indexOf(GuiSettingsManager.guiTheme) + 1) % all.size]
                        GuiSettingsManager.updateGuiTheme(next)
                    },
                    onClose = { OverlayManager.dismissOverlayWindow(this@OverlayClickGUI) }
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategorySidebar(palette)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.contentBg, RoundedCornerShape(12.dp))
                            .border(1.dp, palette.border.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        AnimatedContent(
                            targetState = selectedModuleCategory,
                            transitionSpec = {
                                fadeIn(tween(180)) + slideInHorizontally { it / 4 } togetherWith
                                        fadeOut(tween(150)) + slideOutHorizontally { -it / 4 }
                            },
                            label = "CategoryContent"
                        ) { category ->
                            if (category == ModuleCategory.Config && searchQuery.isBlank() && !onlyFavorites && !onlyActive) {
                                ConfigurationScreen(snackbarHostState = snackbarHostState)
                            } else {
                                ModuleContent(
                                    moduleCategory = category,
                                    searchQuery = searchQuery,
                                    onlyFavorites = onlyFavorites,
                                    onlyActive = onlyActive
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            )
        }
    }

    // =========================================================================
    // 2. PC DROPDOWN PANELS LAYOUT (Multi-Column Vape / Meteor / Rusher Style)
    // =========================================================================
    @Composable
    private fun PanelsDropdownGuiLayout(
        palette: GuiPalette,
        snackbarHostState: SnackbarHostState
    ) {
        val fontFamily = rememberGuiFontFamily()
        val categories = remember { ModuleCategory.entries }
        val collapsedMap = remember { mutableStateMapOf<ModuleCategory, Boolean>() }
        var showConfigModal by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Control Strip for PC Dropdown Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.sidebarBg)
                        .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🐱 Gato Client • PC Panels",
                            color = palette.accent,
                            fontSize = 15.sp,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        CompactSearchBar(palette)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextButton(
                            onClick = { showConfigModal = !showConfigModal },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (showConfigModal) palette.accent else palette.cardBg
                            )
                        ) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = if (showConfigModal) Color.White else palette.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "GUI & Config",
                                color = if (showConfigModal) Color.White else palette.textPrimary,
                                fontSize = 11.sp,
                                fontFamily = fontFamily
                            )
                        }
                        IconButton(
                            onClick = {
                                val all = GUITheme.entries
                                val next = all[(all.indexOf(GuiSettingsManager.guiTheme) + 1) % all.size]
                                GuiSettingsManager.updateGuiTheme(next)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.ViewCarousel, "Switch GUI", tint = palette.accent)
                        }
                        IconButton(
                            onClick = { OverlayManager.dismissOverlayWindow(this@OverlayClickGUI) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.Close, "Close", tint = palette.textSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (showConfigModal) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.contentBg)
                            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {}
                    ) {
                        ConfigurationScreen(snackbarHostState = snackbarHostState)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        categories.filter { it != ModuleCategory.Config }.forEach { category ->
                            val isCollapsed = collapsedMap[category] == true
                            val mods = ModuleManager.modules.filter {
                                !it.private && it.category == category &&
                                        (searchQuery.isBlank() ||
                                                it.name.translatedSelf.contains(searchQuery, ignoreCase = true) ||
                                                it.name.contains(searchQuery, ignoreCase = true))
                            }

                            Column(
                                modifier = Modifier
                                    .width(215.dp)
                                    .heightIn(max = 360.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.windowBgTop)
                                    .border(1.5.dp, palette.border, RoundedCornerShape(12.dp))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {}
                            ) {
                                // Column Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(palette.sidebarBg)
                                        .clickable { collapsedMap[category] = !isCollapsed }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(category.iconResId),
                                            contentDescription = category.name,
                                            tint = palette.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = category.name,
                                            color = palette.textPrimary,
                                            fontSize = 13.sp,
                                            fontFamily = fontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "${mods.count { it.isEnabled }}/${mods.size}",
                                        color = palette.accent,
                                        fontSize = 11.sp,
                                        fontFamily = fontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (!isCollapsed) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(mods.size, key = { mods[it].name }) { idx ->
                                            val mod = mods[idx]
                                            DropdownPanelModuleItem(mod, palette)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    @Composable
    private fun DropdownPanelModuleItem(module: Module, palette: GuiPalette) {
        val fontFamily = rememberGuiFontFamily()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (module.isEnabled) palette.accent.copy(alpha = 0.18f) else palette.cardBg)
                .border(
                    width = 1.dp,
                    color = if (module.isEnabled) palette.accent else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { module.isEnabled = !module.isEnabled }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = module.name.translatedSelf,
                    color = if (module.isEnabled) palette.accent else palette.textPrimary,
                    fontSize = 12.sp,
                    fontFamily = fontFamily,
                    fontWeight = if (module.isEnabled) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (module.values.isNotEmpty()) {
                    IconButton(
                        onClick = { module.isExpanded = !module.isExpanded },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = if (module.isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "Settings",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            if (module.isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.cardExpandedBg)
                        .padding(vertical = 4.dp)
                ) {
                    ModuleSettingsBody(module)
                }
            }
        }
    }

    // =========================================================================
    // 3. SPLIT DASHBOARD LAYOUT (2-Column Grid Left + Live Inspector Right)
    // =========================================================================
    @Composable
    private fun SplitDashboardGuiLayout(
        palette: GuiPalette,
        snackbarHostState: SnackbarHostState
    ) {
        val context = LocalContext.current
        val fontFamily = rememberGuiFontFamily()
        val cornerShape = RoundedCornerShape(GuiSettingsManager.cornerRadiusDp.dp)
        var inspectedModule by remember {
            mutableStateOf<Module?>(ModuleManager.modules.firstOrNull { !it.private })
        }

        val visibleModules = ModuleManager.modules.filter { mod ->
            !mod.private &&
                    (searchQuery.isNotBlank() || onlyFavorites || onlyActive || mod.category == selectedModuleCategory) &&
                    (searchQuery.isBlank() || mod.name.translatedSelf.contains(searchQuery, ignoreCase = true) || mod.name.contains(searchQuery, ignoreCase = true)) &&
                    (!onlyFavorites || mod.isFavorite) &&
                    (!onlyActive || mod.isEnabled)
        }

        Box(
            modifier = Modifier
                .width((GuiSettingsManager.guiWidthDp + 40).coerceAtMost(820).dp)
                .height(GuiSettingsManager.guiHeightDp.dp)
                .clip(cornerShape)
                .background(Brush.verticalGradient(listOf(palette.windowBgTop, palette.windowBgBottom)), cornerShape)
                .border(1.5.dp, palette.border, cornerShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopHeaderBar(
                    palette = palette,
                    onDiscord = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/N2Gejr8Fbp")))
                    },
                    onCycleLayout = {
                        val all = GUITheme.entries
                        val next = all[(all.indexOf(GuiSettingsManager.guiTheme) + 1) % all.size]
                        GuiSettingsManager.updateGuiTheme(next)
                    },
                    onClose = { OverlayManager.dismissOverlayWindow(this@OverlayClickGUI) }
                )

                // Horizontal Pill Category Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.sidebarBg.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModuleCategory.entries.forEach { category ->
                        val isSelected = selectedModuleCategory == category
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) palette.accent else palette.cardBg)
                                .clickable { selectedModuleCategory = category }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(category.iconResId),
                                contentDescription = category.name,
                                tint = if (isSelected) Color.White else palette.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = category.name,
                                color = if (isSelected) Color.White else palette.textPrimary,
                                fontSize = 11.sp,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (selectedModuleCategory == ModuleCategory.Config && searchQuery.isBlank() && !onlyFavorites && !onlyActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        ConfigurationScreen(snackbarHostState = snackbarHostState)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left: 2-Column Bento Grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visibleModules.size, key = { visibleModules[it].name }) { idx ->
                                val mod = visibleModules[idx]
                                val isInspected = inspectedModule == mod
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (mod.isEnabled) palette.accent.copy(alpha = 0.16f) else palette.cardBg)
                                        .border(
                                            width = if (isInspected) 2.dp else 1.dp,
                                            color = if (isInspected) palette.accent else palette.border.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { inspectedModule = mod }
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = mod.name.translatedSelf,
                                            color = if (mod.isEnabled) palette.accent else palette.textPrimary,
                                            fontSize = 12.sp,
                                            fontFamily = fontFamily,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = mod.isEnabled,
                                            onCheckedChange = {
                                                mod.isEnabled = it
                                                inspectedModule = mod
                                            },
                                            modifier = Modifier.scale(0.75f).size(36.dp, 22.dp),
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = palette.accent,
                                                checkedTrackColor = palette.accent.copy(alpha = 0.35f)
                                            )
                                        )
                                    }
                                    Text(
                                        text = "${mod.category.name} • ${mod.values.size} opts",
                                        color = palette.textSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = fontFamily
                                    )
                                }
                            }
                        }

                        // Right: Live Module Inspector
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.contentBg)
                                .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            val targetMod = inspectedModule
                            if (targetMod != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "⚙ ${targetMod.name.translatedSelf}",
                                        color = palette.accent,
                                        fontSize = 14.sp,
                                        fontFamily = fontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = {
                                            targetMod.isFavorite = !targetMod.isFavorite
                                            com.retrivedmods.wclient.util.ConfigAutoSaver.scheduleSave()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (targetMod.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                            contentDescription = null,
                                            tint = if (targetMod.isFavorite) Color(0xFFFFC107) else palette.textSecondary
                                        )
                                    }
                                }
                                HorizontalDivider(color = palette.border.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))
                                ModuleSettingsBody(targetMod)
                            } else {
                                Text(
                                    text = "Select a module on the left to inspect its settings.",
                                    color = palette.textSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = fontFamily,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // =========================================================================
    // 4. COMPACT MOBILE DOCK LAYOUT (Minimalist Bottom Dock + Compact Sheet)
    // =========================================================================
    @Composable
    private fun CompactDockGuiLayout(
        palette: GuiPalette,
        snackbarHostState: SnackbarHostState
    ) {
        val fontFamily = rememberGuiFontFamily()

        Column(
            modifier = Modifier
                .width(520.dp)
                .height(310.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {},
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compact Content Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.contentBg)
                    .border(1.5.dp, palette.border, RoundedCornerShape(18.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🐱 ${selectedModuleCategory.name}",
                            color = palette.accent,
                            fontSize = 14.sp,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        CompactSearchBar(palette)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    val all = GUITheme.entries
                                    val next = all[(all.indexOf(GuiSettingsManager.guiTheme) + 1) % all.size]
                                    GuiSettingsManager.updateGuiTheme(next)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.ViewCarousel, null, tint = palette.accent, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { OverlayManager.dismissOverlayWindow(this@OverlayClickGUI) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.Close, null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (selectedModuleCategory == ModuleCategory.Config && searchQuery.isBlank()) {
                        ConfigurationScreen(snackbarHostState = snackbarHostState)
                    } else {
                        ModuleContent(
                            moduleCategory = selectedModuleCategory,
                            searchQuery = searchQuery,
                            onlyFavorites = onlyFavorites,
                            onlyActive = onlyActive
                        )
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Floating macOS/Mobile Style Dock
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.sidebarBg)
                    .border(1.5.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModuleCategory.entries.forEach { category ->
                    val selected = selectedModuleCategory == category
                    Box(
                        modifier = Modifier
                            .size(if (selected) 38.dp else 32.dp)
                            .clip(CircleShape)
                            .background(if (selected) palette.accent else Color.Transparent)
                            .clickable { selectedModuleCategory = category },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(category.iconResId),
                            contentDescription = category.name,
                            tint = if (selected) Color.White else palette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // Shared Header, Search Bar & Sidebar Components
    // =========================================================================
    @Composable
    private fun TopHeaderBar(
        palette: GuiPalette,
        onDiscord: () -> Unit,
        onCycleLayout: () -> Unit,
        onClose: () -> Unit
    ) {
        val fontFamily = rememberGuiFontFamily()
        val activeCount = ModuleManager.modules.count { it.isEnabled && !it.private }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(palette.sidebarBg)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "🐱 Gato Client",
                    color = palette.accent,
                    fontSize = 16.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(palette.accent.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$activeCount ON",
                        color = palette.accent,
                        fontSize = 10.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                CompactSearchBar(palette)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Favorites filter toggle
                IconButton(
                    onClick = { onlyFavorites = !onlyFavorites },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (onlyFavorites) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Favorites",
                        tint = if (onlyFavorites) Color(0xFFFFC107) else palette.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                // Active modules filter toggle
                IconButton(
                    onClick = { onlyActive = !onlyActive },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Active Only",
                        tint = if (onlyActive) palette.accent else palette.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                // Cycle GUI Layout button
                IconButton(
                    onClick = onCycleLayout,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ViewCarousel,
                        contentDescription = "Cycle GUI Layout",
                        tint = palette.accent,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = onDiscord,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_discord),
                        contentDescription = "Discord",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = palette.textSecondary
                    )
                }
            }
        }
    }

    @Composable
    private fun CompactSearchBar(palette: GuiPalette) {
        val fontFamily = rememberGuiFontFamily()

        Row(
            modifier = Modifier
                .width(155.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(50))
                .background(palette.contentBg)
                .border(1.dp, palette.border, RoundedCornerShape(50))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = "Search",
                tint = palette.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search module...",
                        color = palette.textSecondary.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = fontFamily,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = palette.textPrimary,
                        fontSize = 11.sp,
                        fontFamily = fontFamily
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (searchQuery.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Clear,
                    contentDescription = "Clear",
                    tint = palette.textSecondary,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { searchQuery = "" }
                )
            }
        }
    }

    @Composable
    private fun CategorySidebar(palette: GuiPalette) {
        val categories = remember { ModuleCategory.entries }

        LazyColumn(
            modifier = Modifier
                .width(68.dp)
                .fillMaxHeight()
                .background(palette.sidebarBg, RoundedCornerShape(12.dp))
                .border(1.dp, palette.border.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(vertical = 10.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(categories.size) { index ->
                val category = categories[index]
                CategoryIcon(
                    category = category,
                    palette = palette,
                    isSelected = selectedModuleCategory == category && !onlyFavorites && !onlyActive,
                    onClick = {
                        onlyFavorites = false
                        onlyActive = false
                        selectedModuleCategory = category
                    }
                )
            }
        }
    }

    @Composable
    private fun CategoryIcon(
        category: ModuleCategory,
        palette: GuiPalette,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        val fontFamily = rememberGuiFontFamily()
        val scale by animateFloatAsState(
            targetValue = if (isSelected) 1.06f else 1f,
            animationSpec = spring(dampingRatio = 0.7f),
            label = "catScale"
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.clickable { onClick() }
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .scale(scale)
                    .background(
                        if (isSelected) palette.accent.copy(alpha = 0.2f) else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .let {
                        if (isSelected) it.border(1.dp, palette.accent, RoundedCornerShape(10.dp)) else it
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(category.iconResId),
                    contentDescription = category.name,
                    tint = if (isSelected) palette.accent else palette.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = category.name,
                color = if (isSelected) palette.accent else palette.textSecondary,
                fontSize = 9.sp,
                fontFamily = fontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
