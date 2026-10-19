package com.retrivedmods.wclient.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.retrivedmods.wclient.R
import com.retrivedmods.wclient.overlay.GuiSettingsManager
import com.retrivedmods.wclient.overlay.OverlayManager
import com.retrivedmods.wclient.util.translatedSelf
import kotlin.math.roundToInt

@Composable
fun rememberGuiFontFamily(): FontFamily {
    val useMc = GuiSettingsManager.useMinecraftFont
    return remember(useMc) {
        if (useMc) {
            try {
                FontFamily(Font(R.font.minecraft))
            } catch (_: Exception) {
                FontFamily.Default
            }
        } else {
            FontFamily.Default
        }
    }
}

@Composable
fun ModuleContent(
    moduleCategory: ModuleCategory,
    searchQuery: String = "",
    onlyFavorites: Boolean = false,
    onlyActive: Boolean = false
) {
    val allVisible = remember {
        ModuleManager.modules.filter { !it.private }
    }

    val filtered = allVisible.filter { mod ->
        val matchesCategory = searchQuery.isNotBlank() || onlyFavorites || onlyActive || mod.category == moduleCategory
        val matchesQuery = searchQuery.isBlank() ||
                mod.name.translatedSelf.contains(searchQuery, ignoreCase = true) ||
                mod.name.contains(searchQuery, ignoreCase = true)
        val matchesFav = !onlyFavorites || mod.isFavorite
        val matchesActive = !onlyActive || mod.isEnabled
        matchesCategory && matchesQuery && matchesFav && matchesActive
    }.sortedByDescending { it.isFavorite }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(filtered.size, key = { filtered[it].name }) { i ->
            ModuleCard(filtered[i])
        }
    }
}

@Composable
fun ModuleCard(module: Module) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()
    val bg by animateColorAsState(
        targetValue = if (module.isExpanded) palette.cardExpandedBg else palette.cardBg,
        animationSpec = tween(durationMillis = 240),
        label = "moduleBg"
    )

    val elevation by animateFloatAsState(
        targetValue = if (module.isExpanded) 4f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardElevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { module.isExpanded = !module.isExpanded },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (module.isEnabled) 1.5.dp else 1.dp,
            color = if (module.isEnabled) palette.accent.copy(alpha = 0.65f) else palette.border.copy(alpha = 0.4f)
        ),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                IconButton(
                    onClick = {
                        module.isFavorite = !module.isFavorite
                        com.retrivedmods.wclient.util.ConfigAutoSaver.scheduleSave()
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (module.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (module.isFavorite) Color(0xFFFFC107) else palette.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        module.name.translatedSelf,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = if (module.isEnabled) palette.accent else palette.textPrimary
                    )
                    if (module.values.isNotEmpty()) {
                        Text(
                            text = "${module.values.size} settings • tap to ${if (module.isExpanded) "collapse" else "configure"}",
                            fontSize = 10.sp,
                            fontFamily = fontFamily,
                            color = palette.textSecondary.copy(alpha = 0.75f)
                        )
                    }
                }
                Switch(
                    checked = module.isEnabled,
                    onCheckedChange = { module.isEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = palette.accent,
                        checkedTrackColor = palette.accent.copy(alpha = 0.3f),
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = palette.textSecondary,
                        uncheckedTrackColor = palette.sidebarBg,
                        uncheckedBorderColor = palette.border
                    ),
                    modifier = Modifier
                        .width(50.dp)
                        .height(30.dp)
                )
            }

            if (module.isExpanded) {
                ModuleSettingsBody(module)
            }
        }
    }
}

@Composable
fun ModuleSettingsBody(module: Module) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        module.values.fastForEach {
            when (it) {
                is BoolValue -> BoolValueContent(it)
                is FloatValue -> FloatValueContent(it)
                is IntValue -> IntValueContent(it)
                is ListValue -> ChoiceValueContent(it)
                is EnumValue<*> -> EnumValueContent(it)
                is StringValue -> StringValueContent(it)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .toggleable(
                        value = module.isShortcutDisplayed,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = true
                    ) {
                        module.isShortcutDisplayed = it
                        if (it) OverlayManager.showOverlayWindow(module.overlayShortcutButton)
                        else OverlayManager.dismissOverlayWindow(module.overlayShortcutButton)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = module.isShortcutDisplayed,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        uncheckedColor = palette.textSecondary,
                        checkedColor = palette.accent,
                        checkmarkColor = Color.White
                    )
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.shortcut),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = fontFamily,
                    color = palette.textSecondary
                )
            }

            if (module.values.isNotEmpty()) {
                TextButton(
                    onClick = { module.resetValues() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Reset",
                        tint = palette.accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Reset",
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontFamily = fontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceValueContent(value: ListValue) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
        Text(
            value.name.translatedSelf,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = fontFamily,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            value.listItems.forEach { item ->
                ElevatedFilterChip(
                    selected = value.value == item,
                    onClick = { if (value.value != item) value.value = item },
                    label = {
                        Text(
                            item.name.translatedSelf,
                            fontFamily = fontFamily,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.height(30.dp),
                    enabled = true,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = palette.sidebarBg,
                        selectedContainerColor = palette.accent,
                        labelColor = palette.textSecondary,
                        selectedLabelColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = value.value == item,
                        borderColor = palette.border,
                        selectedBorderColor = palette.accent
                    )
                )
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun FloatValueContent(value: FloatValue) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) {
        Row {
            Text(
                value.name.translatedSelf,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = fontFamily,
                color = palette.textSecondary
            )
            Spacer(Modifier.weight(1f))
            Text(
                String.format("%.2f", value.value),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
        }

        val colors = SliderDefaults.colors(
            thumbColor = palette.accent,
            activeTrackColor = palette.accent,
            activeTickColor = palette.accent,
            inactiveTickColor = palette.border,
            inactiveTrackColor = palette.border
        )

        Slider(
            value = value.value,
            onValueChange = {
                val rounded = ((it * 100.0).roundToInt() / 100.0).toFloat()
                if (value.value != rounded) value.value = rounded
            },
            valueRange = value.range,
            colors = colors,
            enabled = true
        )
    }
}

@Composable
private fun IntValueContent(value: IntValue) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) {
        Row {
            Text(
                value.name.translatedSelf,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = fontFamily,
                color = palette.textSecondary
            )
            Spacer(Modifier.weight(1f))
            Text(
                value.value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
        }

        val colors = SliderDefaults.colors(
            thumbColor = palette.accent,
            activeTrackColor = palette.accent,
            activeTickColor = palette.accent,
            inactiveTickColor = palette.border,
            inactiveTrackColor = palette.border
        )

        Slider(
            value = value.value.toFloat(),
            onValueChange = {
                val next = it.roundToInt()
                if (value.value != next) value.value = next
            },
            valueRange = value.range.toFloatRange(),
            colors = colors,
            enabled = true
        )
    }
}

@Composable
private fun BoolValueContent(value: BoolValue) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
            .fillMaxWidth()
            .toggleable(
                value = value.value,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = true
            ) { value.value = it },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            value.name.translatedSelf,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = fontFamily,
            color = palette.textSecondary
        )
        Spacer(Modifier.weight(1f))
        Checkbox(
            checked = value.value,
            onCheckedChange = null,
            modifier = Modifier.padding(0.dp),
            enabled = true,
            colors = CheckboxDefaults.colors(
                uncheckedColor = palette.textSecondary,
                checkedColor = palette.accent,
                checkmarkColor = Color.White
            )
        )
    }
}

@Composable
private fun <T : Enum<T>> EnumValueContent(value: EnumValue<T>) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
        Text(
            value.name.translatedSelf,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = fontFamily,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            value.enumClass.enumConstants?.forEach { option ->
                ElevatedFilterChip(
                    selected = value.value == option,
                    onClick = { if (value.value != option) value.value = option },
                    label = {
                        Text(
                            option.name.translatedSelf,
                            fontFamily = fontFamily,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.height(30.dp),
                    enabled = true,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = palette.sidebarBg,
                        selectedContainerColor = palette.accent,
                        labelColor = palette.textSecondary,
                        selectedLabelColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = value.value == option,
                        borderColor = palette.border,
                        selectedBorderColor = palette.accent
                    )
                )
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun StringValueContent(value: StringValue) {
    val palette = GuiSettingsManager.currentPalette()
    val fontFamily = rememberGuiFontFamily()

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
        Text(
            value.name.translatedSelf,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = fontFamily,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value.value,
            onValueChange = { value.value = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.border,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary,
                cursorColor = palette.accent
            ),
            shape = MaterialTheme.shapes.small
        )
    }
}

private fun IntRange.toFloatRange() = first.toFloat()..last.toFloat()
