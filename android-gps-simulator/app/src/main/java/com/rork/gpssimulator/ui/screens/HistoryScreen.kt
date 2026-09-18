package com.rork.gpssimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.rork.gpssimulator.data.model.HistoryEntry
import com.rork.gpssimulator.data.model.SessionKind
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.components.ConfirmDialog
import com.rork.gpssimulator.ui.components.EmptyState
import com.rork.gpssimulator.ui.components.InfoPill
import com.rork.gpssimulator.ui.theme.LocalAppColors
import com.rork.gpssimulator.ui.theme.MonoValueStyle
import com.rork.gpssimulator.util.Format

private fun sessionIcon(kind: SessionKind): ImageVector = when (kind) {
    SessionKind.STATIC -> Icons.Default.LocationOn
    SessionKind.ROUTE -> Icons.Default.Route
    SessionKind.JOYSTICK -> Icons.Default.Gamepad
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    bottomInset: androidx.compose.ui.unit.Dp,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val history by viewModel.history.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings[K.history_title],
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = strings[K.clear_history],
                                tint = appColors.danger,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when {
            !settings.saveHistory && history.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Default.History,
                        title = strings[K.history_disabled],
                        body = strings[K.history_disabled_body],
                    )
                }
            }

            history.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Default.History,
                        title = strings[K.no_history],
                        body = strings[K.no_history_body],
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = bottomInset + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(history, key = { it.id }) { entry ->
                        HistoryCard(
                            entry = entry,
                            units = settings.units,
                            onRestore = {
                                viewModel.selectSaved(
                                    com.rork.gpssimulator.data.model.SavedLocation(
                                        id = entry.id,
                                        name = entry.name,
                                        address = entry.address,
                                        point = entry.point,
                                    ),
                                )
                                onRestore()
                            },
                            onDelete = { viewModel.deleteHistory(entry.id) },
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = strings[K.clear_history],
            body = strings[K.clear_history_confirm],
            confirmLabel = strings[K.delete],
            cancelLabel = strings[K.cancel],
            destructive = true,
            onConfirm = { viewModel.clearHistory() },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    units: com.rork.gpssimulator.data.model.UnitSystem,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onRestore)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = sessionIcon(entry.kind),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = Format.timestamp(entry.startedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.muted,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = strings[K.delete],
                    tint = appColors.muted,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = Format.coordPair(entry.point),
            style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill(text = Format.elapsed(entry.durationMs))
            InfoPill(
                text = when (entry.kind) {
                    SessionKind.STATIC -> strings[K.selected_point]
                    SessionKind.ROUTE -> strings[K.route_simulator]
                    SessionKind.JOYSTICK -> strings[K.joystick_mode]
                },
                color = appColors.muted,
            )
        }
    }
}
