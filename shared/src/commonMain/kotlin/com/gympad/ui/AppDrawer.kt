package com.gympad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gympad.domain.AppLanguage
import com.gympad.ui.strings.LocalStrings

enum class Destination { WORKOUT, FOOD }

@Composable
fun AppDrawer(
    current: Destination,
    language: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onNavigate: (Destination) -> Unit,
    onRoutines: () -> Unit,
    onAiPhotos: () -> Unit,
    onBackup: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = colors.surfaceContainer,
        drawerContentColor = colors.onSurface,
        drawerTonalElevation = 0.dp,
    ) {
        Text(
            "GymPad",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 16.dp).semantics { heading() },
        )
        DrawerItem(strings.workout, GymIcons.Dumbbell, current == Destination.WORKOUT) { onNavigate(Destination.WORKOUT) }
        DrawerItem(strings.routines, GymIcons.Routines, selected = false, onClick = onRoutines)
        DrawerItem(strings.food, GymIcons.Food, current == Destination.FOOD) { onNavigate(Destination.FOOD) }
        DrawerItem(strings.aiPhotos, GymIcons.Camera, selected = false, onClick = onAiPhotos)
        HorizontalDivider(
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            color = colors.outlineVariant,
        )
        DrawerItem(strings.backup, GymIcons.Export, selected = false, onClick = onBackup)
        Spacer(Modifier.weight(1f))
        SectionLabel(strings.languageLabel, Modifier.padding(start = 24.dp, bottom = 8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppLanguage.entries.forEach { option ->
                LanguageOption(
                    option = option,
                    selected = option == language,
                    onClick = { onSelectLanguage(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = colors.surfaceContainerHighest,
            selectedIconColor = colors.onSurface,
            selectedTextColor = colors.onSurface,
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = colors.onSurfaceVariant,
            unselectedTextColor = colors.onSurface,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun LanguageOption(option: AppLanguage, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .height(46.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            option.nativeName,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        )
    }
}
