package com.rork.gpssimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.SavedKind
import com.rork.gpssimulator.data.model.SavedLocation
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.components.ConfirmDialog
import com.rork.gpssimulator.ui.components.EmptyState
import com.rork.gpssimulator.ui.theme.LocalAppColors
import com.rork.gpssimulator.ui.theme.MonoValueStyle
import com.rork.gpssimulator.util.Format

/** Icon choices offered when saving a location. */
val LocationIcons: List<Pair<String, ImageVector>> = listOf(
    "pin" to Icons.Default.LocationOn,
    "home" to Icons.Default.Home,
    "work" to Icons.Default.Business,
    "test" to Icons.Default.Science,
    "flag" to Icons.Default.Flag,
    "star" to Icons.Default.Star,
)

fun iconFor(key: String): ImageVector =
    LocationIcons.firstOrNull { it.first == key }?.second ?: Icons.Default.LocationOn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    viewModel: AppViewModel,
    bottomInset: androidx.compose.ui.unit.Dp,
    initialTab: Int = 0,
    onUseLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val saved by viewModel.savedLocations.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var tabIndex by remember { mutableStateOf(initialTab) }
    var editing by remember { mutableStateOf<SavedLocation?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavedLocation?>(null) }

    val kind = if (tabIndex == 0) SavedKind.FAVORITE else SavedKind.PRESET
    val visible = saved.filter { it.kind == kind }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = strings[K.presets_title],
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                TabRow(
                    selectedTabIndex = tabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        text = {
                            Text(
                                strings[K.favorites_tab],
                                style = MaterialTheme.typography.titleSmall,
                            )
                        },
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        text = {
                            Text(
                                strings[K.dev_presets_tab],
                                style = MaterialTheme.typography.titleSmall,
                            )
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = null
                    showEditor = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = bottomInset),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = strings[K.add_coordinate],
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) { padding ->
        if (visible.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = if (kind == SavedKind.FAVORITE) Icons.Default.Star else Icons.Default.Science,
                    title = if (kind == SavedKind.FAVORITE) strings[K.no_favorites] else strings[K.no_presets],
                    body = if (kind == SavedKind.FAVORITE) {
                        strings[K.no_favorites_body]
                    } else {
                        strings[K.no_presets_body]
                    },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = bottomInset + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visible, key = { it.id }) { location ->
                    SavedLocationCard(
                        location = location,
                        units = settings.units,
                        onUse = {
                            viewModel.selectSaved(location)
                            onUseLocation()
                        },
                        onShowOnMap = {
                            viewModel.moveCamera(location.point, 16f)
                            onUseLocation()
                        },
                        onEdit = {
                            editing = location
                            showEditor = true
                        },
                        onDelete = { pendingDelete = location },
                    )
                }
            }
        }
    }

    if (showEditor) {
        LocationEditorSheet(
            existing = editing,
            defaultKind = kind,
            fallbackPoint = viewModel.selected.collectAsState().value?.point
                ?: LatLng(settings.lastCameraLat, settings.lastCameraLng),
            onSave = { name, address, point, savedKind, iconKey, notes ->
                viewModel.saveLocation(
                    name = name,
                    address = address,
                    point = point,
                    kind = savedKind,
                    iconKey = iconKey,
                    notes = notes,
                    id = editing?.id,
                )
                viewModel.postMessage(strings[K.location_saved])
                showEditor = false
                editing = null
            },
            onDismiss = {
                showEditor = false
                editing = null
            },
        )
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = strings[K.delete_location],
            body = strings[K.delete_location_confirm],
            confirmLabel = strings[K.delete],
            cancelLabel = strings[K.cancel],
            destructive = true,
            onConfirm = { viewModel.deleteSaved(target.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun SavedLocationCard(
    location: SavedLocation,
    units: com.rork.gpssimulator.data.model.UnitSystem,
    onUse: () -> Unit,
    onShowOnMap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onUse)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconFor(location.iconKey),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (location.address.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = location.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors.muted,
                        maxLines = 1,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = appColors.muted,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(strings[K.use_location], style = MaterialTheme.typography.bodyLarge)
                        },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onUse()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(strings[K.show_on_map], style = MaterialTheme.typography.bodyLarge)
                        },
                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onShowOnMap()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(strings[K.edit], style = MaterialTheme.typography.bodyLarge) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = strings[K.delete],
                                style = MaterialTheme.typography.bodyLarge,
                                color = appColors.danger,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = appColors.danger,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = Format.coordPair(location.point),
            style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
            color = MaterialTheme.colorScheme.primary,
        )

        if (location.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = location.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.muted,
                maxLines = 3,
            )
        }
    }
}
