package com.rork.gpssimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.data.model.Geofence
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.components.ConfirmDialog
import com.rork.gpssimulator.ui.components.EmptyState
import com.rork.gpssimulator.ui.components.InfoPill
import com.rork.gpssimulator.ui.components.StatusDot
import com.rork.gpssimulator.ui.map.MapCircle
import com.rork.gpssimulator.ui.map.MapMarker
import com.rork.gpssimulator.ui.map.MapView
import com.rork.gpssimulator.ui.map.rememberMapCameraState
import com.rork.gpssimulator.ui.theme.LocalAppColors
import com.rork.gpssimulator.ui.theme.MonoValueStyle
import com.rork.gpssimulator.util.Format
import kotlin.math.roundToInt

/** Geofence testing: circular zones evaluated against the active mock position. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current

    val fences by viewModel.geofences.collectAsState()
    val inside by viewModel.geofenceInside.collectAsState()
    val events by viewModel.geofenceEvents.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val activePoint by viewModel.activePoint.collectAsState()
    val realSample by viewModel.realSample.collectAsState()

    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Geofence?>(null) }

    val focusPoint = activePoint ?: realSample?.point
        ?: LatLng(settings.lastCameraLat, settings.lastCameraLng)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings[K.geofence_title],
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings[K.close],
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEditor = true },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(strings[K.add_geofence], style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { padding ->
        if (fences.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Default.Adjust,
                    title = strings[K.no_geofences],
                    body = strings[K.no_geofences_body],
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 110.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    // Overview map of every zone plus the monitored position.
                    val camera = rememberMapCameraState(focusPoint, 13f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(18.dp)),
                    ) {
                        MapView(
                            camera = camera,
                            mapType = settings.mapType,
                            circles = fences.map { fence ->
                                val isInside = inside.contains(fence.id)
                                MapCircle(
                                    center = fence.center,
                                    radiusMeters = fence.radiusMeters,
                                    strokeColor = if (isInside) appColors.success else MaterialTheme.colorScheme.primary,
                                    fillColor = (if (isInside) appColors.success else MaterialTheme.colorScheme.primary)
                                        .copy(alpha = 0.14f),
                                )
                            },
                            markers = listOfNotNull(
                                activePoint?.let {
                                    MapMarker(it, MaterialTheme.colorScheme.primary, pulsing = true)
                                } ?: realSample?.let {
                                    MapMarker(it.point, appColors.success)
                                },
                            ),
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings[K.monitoring],
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors.muted,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }

                items(fences, key = { it.id }) { fence ->
                    GeofenceCard(
                        fence = fence,
                        isInside = inside.contains(fence.id),
                        units = settings.units,
                        onDelete = { pendingDelete = fence },
                    )
                }

                if (events.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = strings[K.geofence_events],
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                    }
                    items(events.take(30), key = { "${it.geofenceId}-${it.timestamp}" }) { event ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (event.entered) Icons.Default.Login else Icons.Default.Logout,
                                contentDescription = null,
                                tint = if (event.entered) appColors.success else appColors.warning,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = event.geofenceName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (event.entered) strings[K.entered] else strings[K.exited],
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (event.entered) appColors.success else appColors.warning,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = Format.clockTime(event.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors.muted,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        GeofenceEditorSheet(
            defaultCenter = focusPoint,
            mapType = settings.mapType,
            units = settings.units,
            onSave = { name, center, radius ->
                viewModel.addGeofence(name, center, radius)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = strings[K.delete_geofence],
            body = target.name,
            confirmLabel = strings[K.delete],
            cancelLabel = strings[K.cancel],
            destructive = true,
            onConfirm = { viewModel.deleteGeofence(target.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun GeofenceCard(
    fence: Geofence,
    isInside: Boolean,
    units: com.rork.gpssimulator.data.model.UnitSystem,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        (if (isInside) appColors.success else MaterialTheme.colorScheme.primary)
                            .copy(alpha = 0.12f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Adjust,
                    contentDescription = null,
                    tint = if (isInside) appColors.success else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fence.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Format.coordPair(fence.center),
                    style = MonoValueStyle.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = strings[K.delete], tint = appColors.muted)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill(text = Format.shortDistance(fence.radiusMeters, units))
            InfoPill(
                text = if (isInside) strings[K.inside] else strings[K.outside],
                color = if (isInside) appColors.success else appColors.muted,
                leading = {
                    StatusDot(if (isInside) appColors.success else appColors.muted, size = 9)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeofenceEditorSheet(
    defaultCenter: LatLng,
    mapType: com.rork.gpssimulator.data.model.MapType,
    units: com.rork.gpssimulator.data.model.UnitSystem,
    onSave: (String, LatLng, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf("") }
    var radius by remember { mutableFloatStateOf(150f) }
    var latText by remember { mutableStateOf(Format.coord(defaultCenter.lat)) }
    var lngText by remember { mutableStateOf(Format.coord(defaultCenter.lng)) }

    val lat = latText.trim().toDoubleOrNull()
    val lng = lngText.trim().toDoubleOrNull()
    val center = if (lat != null && lng != null) LatLng(lat, lng) else null
    val isValid = name.isNotBlank() && center?.isValid() == true && radius > 0

    val camera = rememberMapCameraState(defaultCenter, 14f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = strings[K.add_geofence],
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings[K.geofence_name], style = MaterialTheme.typography.bodyLarge) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(18.dp)),
            ) {
                MapView(
                    camera = camera,
                    mapType = mapType,
                    circles = center?.let {
                        listOf(
                            MapCircle(
                                center = it,
                                radiusMeters = radius.toDouble(),
                                strokeColor = MaterialTheme.colorScheme.primary,
                                fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            ),
                        )
                    } ?: emptyList(),
                    onMapTap = { tapped ->
                        latText = Format.coord(tapped.lat)
                        lngText = Format.coord(tapped.lng)
                    },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = strings[K.tap_map_to_add],
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.muted,
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text(strings[K.lat], style = MaterialTheme.typography.bodyLarge) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text(strings[K.lng], style = MaterialTheme.typography.bodyLarge) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings[K.geofence_radius],
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Format.shortDistance(radius.toDouble(), units),
                    style = MonoValueStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = radius,
                onValueChange = { radius = it },
                valueRange = 10f..2000f,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = { center?.let { onSave(name, it, radius.toDouble()) } },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = strings[K.save],
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
