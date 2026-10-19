package com.retrivedmods.wclient.overlay

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import com.retrivedmods.wclient.application.AppContext

enum class GUITheme(val displayName: String, val description: String) {
    CLASSIC("Classic Gato", "Ventana lateral con iconos, buscador y filtros"),
    PANELS("PC Dropdown", "Columnas desplegables por categoría estilo cliente PC"),
    DASHBOARD("Split Dashboard", "Vista panorámica con Grid + Inspector lateral de ajustes"),
    COMPACT("Compact Dock", "Dock minimalista flotante con tarjetas rápidas")
}

enum class ColorPreset(
    val displayName: String,
    val accent: Color,
    val accentSecondary: Color,
    val windowBgTop: Color,
    val windowBgBottom: Color,
    val sidebarBg: Color,
    val contentBg: Color,
    val cardBg: Color,
    val cardExpandedBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val isDark: Boolean
) {
    SAKURA_LIGHT(
        displayName = "Sakura Pink",
        accent = Color(0xFFFF5CA6),
        accentSecondary = Color(0xFFFF8FC5),
        windowBgTop = Color(0xFFFFFFFF),
        windowBgBottom = Color(0xFFFFD6E8),
        sidebarBg = Color(0xFFFFE6F0),
        contentBg = Color(0xFFFFFFFF),
        cardBg = Color(0xFFFFF3F8),
        cardExpandedBg = Color(0xFFFFE6F0),
        textPrimary = Color(0xFF4A2438),
        textSecondary = Color(0xFF9C7288),
        border = Color(0xFFFFC2DD),
        isDark = false
    ),
    DARK_CAT(
        displayName = "Dark Cat OLED",
        accent = Color(0xFFFF5CA6),
        accentSecondary = Color(0xFFFF8FC5),
        windowBgTop = Color(0xFF140D12),
        windowBgBottom = Color(0xFF1F121B),
        sidebarBg = Color(0xFF1C1018),
        contentBg = Color(0xFF120B10),
        cardBg = Color(0xFF1E131B),
        cardExpandedBg = Color(0xFF291824),
        textPrimary = Color(0xFFFFEEF6),
        textSecondary = Color(0xFFC29AB0),
        border = Color(0xFF4A253B),
        isDark = true
    ),
    CYBERPUNK(
        displayName = "Cyberpunk Neon",
        accent = Color(0xFF00F5D4),
        accentSecondary = Color(0xFFFF2A85),
        windowBgTop = Color(0xFF0A0E17),
        windowBgBottom = Color(0xFF121829),
        sidebarBg = Color(0xFF0F1523),
        contentBg = Color(0xFF0B0F1A),
        cardBg = Color(0xFF141C2E),
        cardExpandedBg = Color(0xFF1B263E),
        textPrimary = Color(0xFFE6FAFF),
        textSecondary = Color(0xFF7FA6C4),
        border = Color(0xFF1E3A5F),
        isDark = true
    ),
    CRIMSON_HVH(
        displayName = "Crimson 2b2t",
        accent = Color(0xFFFF2E4C),
        accentSecondary = Color(0xFFFF6B81),
        windowBgTop = Color(0xFF14090B),
        windowBgBottom = Color(0xFF220D12),
        sidebarBg = Color(0xFF1A0B0E),
        contentBg = Color(0xFF110709),
        cardBg = Color(0xFF1F0E12),
        cardExpandedBg = Color(0xFF2B1319),
        textPrimary = Color(0xFFFFECEF),
        textSecondary = Color(0xFFB8848D),
        border = Color(0xFF4F1C26),
        isDark = true
    ),
    AMETHYST(
        displayName = "Amethyst Void",
        accent = Color(0xFFB565FF),
        accentSecondary = Color(0xFFD4A5FF),
        windowBgTop = Color(0xFF110B1A),
        windowBgBottom = Color(0xFF1C112B),
        sidebarBg = Color(0xFF160E22),
        contentBg = Color(0xFF0E0916),
        cardBg = Color(0xFF1A1128),
        cardExpandedBg = Color(0xFF241738),
        textPrimary = Color(0xFFF3E8FF),
        textSecondary = Color(0xFFA38BBF),
        border = Color(0xFF3E275E),
        isDark = true
    ),
    EMERALD(
        displayName = "Emerald Matrix",
        accent = Color(0xFF00E676),
        accentSecondary = Color(0xFF69F0AE),
        windowBgTop = Color(0xFF09140F),
        windowBgBottom = Color(0xFF0F2219),
        sidebarBg = Color(0xFF0C1A13),
        contentBg = Color(0xFF07100C),
        cardBg = Color(0xFF11221A),
        cardExpandedBg = Color(0xFF172E23),
        textPrimary = Color(0xFFE8FFF2),
        textSecondary = Color(0xFF80B39A),
        border = Color(0xFF1E4633),
        isDark = true
    ),
    CUSTOM(
        displayName = "Custom RGB",
        accent = Color(0xFFFF5CA6),
        accentSecondary = Color(0xFFFF8FC5),
        windowBgTop = Color(0xFF141018),
        windowBgBottom = Color(0xFF1E1724),
        sidebarBg = Color(0xFF18121E),
        contentBg = Color(0xFF100C14),
        cardBg = Color(0xFF1C1624),
        cardExpandedBg = Color(0xFF261E30),
        textPrimary = Color(0xFFF5F0FA),
        textSecondary = Color(0xFFA89BB8),
        border = Color(0xFF3D304C),
        isDark = true
    )
}

enum class FloatingButtonShape(val displayName: String) {
    ROUNDED_SQUARE("Rounded"),
    CIRCLE("Circle"),
    PILL_BADGE("Pill Badge")
}

data class GuiPalette(
    val accent: Color,
    val accentSecondary: Color,
    val windowBgTop: Color,
    val windowBgBottom: Color,
    val sidebarBg: Color,
    val contentBg: Color,
    val cardBg: Color,
    val cardExpandedBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val isDark: Boolean
)

object GuiSettingsManager {

    private var initialized = false

    var guiTheme by mutableStateOf(GUITheme.CLASSIC)
        private set

    var colorPreset by mutableStateOf(ColorPreset.SAKURA_LIGHT)
        private set

    var customAccentR by mutableIntStateOf(255)
        private set
    var customAccentG by mutableIntStateOf(92)
        private set
    var customAccentB by mutableIntStateOf(166)
        private set
    var customDarkMode by mutableStateOf(true)
        private set

    var enableBlur by mutableStateOf(false)
        private set
    var blurRadius by mutableIntStateOf(35)
        private set
    var dimAmount by mutableFloatStateOf(0.45f)
        private set
    var guiWidthDp by mutableIntStateOf(640)
        private set
    var guiHeightDp by mutableIntStateOf(355)
        private set
    var guiOpacity by mutableFloatStateOf(0.96f)
        private set
    var cornerRadiusDp by mutableIntStateOf(20)
        private set
    var useMinecraftFont by mutableStateOf(false)
        private set

    var buttonSizeDp by mutableIntStateOf(52)
        private set
    var buttonShape by mutableStateOf(FloatingButtonShape.ROUNDED_SQUARE)
        private set
    var showActiveBadge by mutableStateOf(true)
        private set
    var buttonBorderGlow by mutableStateOf(true)
        private set
    var lockButtonDrag by mutableStateOf(false)
        private set
    var lockShortcutsDrag by mutableStateOf(false)
        private set
    var shortcutScale by mutableFloatStateOf(1.0f)
        private set
    var overlayOpacity by mutableFloatStateOf(1.0f)
        private set
    var shortcutOpacity by mutableFloatStateOf(1.0f)
        private set

    private fun prefs(context: Context? = null) =
        (context ?: if (AppContext.isInitialized) AppContext.instance else null)
            ?.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun ensureLoaded(context: Context? = null) {
        if (initialized) return
        val sp = prefs(context) ?: return
        initialized = true
        guiTheme = runCatching {
            GUITheme.valueOf(sp.getString("gui_theme", GUITheme.CLASSIC.name) ?: GUITheme.CLASSIC.name)
        }.getOrDefault(GUITheme.CLASSIC)
        colorPreset = runCatching {
            ColorPreset.valueOf(sp.getString("color_preset", ColorPreset.SAKURA_LIGHT.name) ?: ColorPreset.SAKURA_LIGHT.name)
        }.getOrDefault(ColorPreset.SAKURA_LIGHT)
        customAccentR = sp.getInt("custom_r", 255).coerceIn(0, 255)
        customAccentG = sp.getInt("custom_g", 92).coerceIn(0, 255)
        customAccentB = sp.getInt("custom_b", 166).coerceIn(0, 255)
        customDarkMode = sp.getBoolean("custom_dark", true)

        enableBlur = sp.getBoolean("enable_blur", false)
        blurRadius = sp.getInt("blur_radius", 35).coerceIn(5, 80)
        dimAmount = sp.getFloat("dim_amount", 0.45f).coerceIn(0f, 0.85f)
        guiWidthDp = sp.getInt("gui_width_dp", 640).coerceIn(460, 820)
        guiHeightDp = sp.getInt("gui_height_dp", 355).coerceIn(260, 480)
        guiOpacity = sp.getFloat("gui_opacity", 0.96f).coerceIn(0.5f, 1.0f)
        cornerRadiusDp = sp.getInt("corner_radius_dp", 20).coerceIn(4, 32)
        useMinecraftFont = sp.getBoolean("use_mc_font", false)

        buttonSizeDp = sp.getInt("button_size_dp", 52).coerceIn(36, 76)
        buttonShape = runCatching {
            FloatingButtonShape.valueOf(sp.getString("button_shape", FloatingButtonShape.ROUNDED_SQUARE.name)!!)
        }.getOrDefault(FloatingButtonShape.ROUNDED_SQUARE)
        showActiveBadge = sp.getBoolean("show_active_badge", true)
        buttonBorderGlow = sp.getBoolean("button_border_glow", true)
        lockButtonDrag = sp.getBoolean("lock_button_drag", false)
        lockShortcutsDrag = sp.getBoolean("lock_shortcuts_drag", false)
        shortcutScale = sp.getFloat("shortcut_scale", 1.0f).coerceIn(0.7f, 1.4f)
        overlayOpacity = sp.getFloat("overlay_opacity", 1.0f).coerceIn(0.1f, 1.0f)
        shortcutOpacity = sp.getFloat("shortcut_opacity", 1.0f).coerceIn(0.1f, 1.0f)
    }

    fun updateGuiTheme(theme: GUITheme) {
        guiTheme = theme
        prefs()?.edit { putString("gui_theme", theme.name) }
    }

    fun updateColorPreset(preset: ColorPreset) {
        colorPreset = preset
        prefs()?.edit { putString("color_preset", preset.name) }
    }

    fun updateCustomRgb(r: Int = customAccentR, g: Int = customAccentG, b: Int = customAccentB, dark: Boolean = customDarkMode) {
        customAccentR = r.coerceIn(0, 255)
        customAccentG = g.coerceIn(0, 255)
        customAccentB = b.coerceIn(0, 255)
        customDarkMode = dark
        prefs()?.edit {
            putInt("custom_r", customAccentR)
            putInt("custom_g", customAccentG)
            putInt("custom_b", customAccentB)
            putBoolean("custom_dark", customDarkMode)
        }
    }

    fun updateWindowStyle(
        blur: Boolean = enableBlur,
        blurRad: Int = blurRadius,
        dim: Float = dimAmount,
        widthDp: Int = guiWidthDp,
        heightDp: Int = guiHeightDp,
        opacity: Float = guiOpacity,
        cornerDp: Int = cornerRadiusDp,
        mcFont: Boolean = useMinecraftFont
    ) {
        enableBlur = blur
        blurRadius = blurRad.coerceIn(5, 80)
        dimAmount = dim.coerceIn(0f, 0.85f)
        guiWidthDp = widthDp.coerceIn(460, 820)
        guiHeightDp = heightDp.coerceIn(260, 480)
        guiOpacity = opacity.coerceIn(0.5f, 1.0f)
        cornerRadiusDp = cornerDp.coerceIn(4, 32)
        useMinecraftFont = mcFont
        prefs()?.edit {
            putBoolean("enable_blur", enableBlur)
            putInt("blur_radius", blurRadius)
            putFloat("dim_amount", dimAmount)
            putInt("gui_width_dp", guiWidthDp)
            putInt("gui_height_dp", guiHeightDp)
            putFloat("gui_opacity", guiOpacity)
            putInt("corner_radius_dp", cornerRadiusDp)
            putBoolean("use_mc_font", useMinecraftFont)
        }
    }

    fun updateFloatingControls(
        btnSize: Int = buttonSizeDp,
        shape: FloatingButtonShape = buttonShape,
        badge: Boolean = showActiveBadge,
        glow: Boolean = buttonBorderGlow,
        lockBtn: Boolean = lockButtonDrag,
        lockShort: Boolean = lockShortcutsDrag,
        shortScale: Float = shortcutScale,
        btnOpacity: Float = overlayOpacity,
        shortOpacity: Float = shortcutOpacity
    ) {
        buttonSizeDp = btnSize.coerceIn(36, 76)
        buttonShape = shape
        showActiveBadge = badge
        buttonBorderGlow = glow
        lockButtonDrag = lockBtn
        lockShortcutsDrag = lockShort
        shortcutScale = shortScale.coerceIn(0.7f, 1.4f)
        overlayOpacity = btnOpacity.coerceIn(0.1f, 1.0f)
        shortcutOpacity = shortOpacity.coerceIn(0.1f, 1.0f)
        prefs()?.edit {
            putInt("button_size_dp", buttonSizeDp)
            putString("button_shape", buttonShape.name)
            putBoolean("show_active_badge", showActiveBadge)
            putBoolean("button_border_glow", buttonBorderGlow)
            putBoolean("lock_button_drag", lockButtonDrag)
            putBoolean("lock_shortcuts_drag", lockShortcutsDrag)
            putFloat("shortcut_scale", shortcutScale)
            putFloat("overlay_opacity", overlayOpacity)
            putFloat("shortcut_opacity", shortcutOpacity)
        }
        OverlayManager.updateOverlayOpacity(overlayOpacity)
        OverlayManager.updateShortcutOpacity(shortcutOpacity)
    }

    fun currentPalette(): GuiPalette {
        val p = colorPreset
        if (p != ColorPreset.CUSTOM) {
            return GuiPalette(
                accent = p.accent,
                accentSecondary = p.accentSecondary,
                windowBgTop = p.windowBgTop.copy(alpha = guiOpacity),
                windowBgBottom = p.windowBgBottom.copy(alpha = guiOpacity),
                sidebarBg = p.sidebarBg.copy(alpha = guiOpacity),
                contentBg = p.contentBg.copy(alpha = guiOpacity),
                cardBg = p.cardBg,
                cardExpandedBg = p.cardExpandedBg,
                textPrimary = p.textPrimary,
                textSecondary = p.textSecondary,
                border = p.border,
                isDark = p.isDark
            )
        }
        val acc = Color(customAccentR / 255f, customAccentG / 255f, customAccentB / 255f)
        val accSec = Color(
            ((customAccentR + 60).coerceAtMost(255)) / 255f,
            ((customAccentG + 60).coerceAtMost(255)) / 255f,
            ((customAccentB + 60).coerceAtMost(255)) / 255f
        )
        return if (customDarkMode) {
            GuiPalette(
                accent = acc,
                accentSecondary = accSec,
                windowBgTop = Color(0xFF121016).copy(alpha = guiOpacity),
                windowBgBottom = Color(0xFF1C1722).copy(alpha = guiOpacity),
                sidebarBg = Color(0xFF17131C).copy(alpha = guiOpacity),
                contentBg = Color(0xFF0E0B12).copy(alpha = guiOpacity),
                cardBg = Color(0xFF1A1522),
                cardExpandedBg = Color(0xFF231C2E),
                textPrimary = Color(0xFFF5F2FA),
                textSecondary = Color(0xFFA69AB5),
                border = acc.copy(alpha = 0.35f),
                isDark = true
            )
        } else {
            GuiPalette(
                accent = acc,
                accentSecondary = accSec,
                windowBgTop = Color(0xFFFFFFFF).copy(alpha = guiOpacity),
                windowBgBottom = acc.copy(alpha = 0.16f),
                sidebarBg = acc.copy(alpha = 0.12f),
                contentBg = Color(0xFFFFFFFF).copy(alpha = guiOpacity),
                cardBg = acc.copy(alpha = 0.06f),
                cardExpandedBg = acc.copy(alpha = 0.14f),
                textPrimary = Color(0xFF281822),
                textSecondary = Color(0xFF7A5C6E),
                border = acc.copy(alpha = 0.35f),
                isDark = false
            )
        }
    }
}
