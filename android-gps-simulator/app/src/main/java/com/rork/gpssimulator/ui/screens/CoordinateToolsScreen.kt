package com.rork.gpssimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.location.GeocodingService
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.components.GroupCard
import com.rork.gpssimulator.ui.components.SectionHeader
import com.rork.gpssimulator.ui.theme.LocalAppColors
import com.rork.gpssimulator.ui.theme.MonoValueStyle
import com.rork.gpssimulator.util.Format

/** Manual coordinate entry, DMS conversion and distance calculation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinateToolsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onUseCoordinate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()

    var latText by remember { mutableStateOf("") }
    var lngText by remember { mutableStateOf("") }
    var pasteText by remember { mutableStateOf("") }

    var aLat by remember { mutableStateOf("") }
    var aLng by remember { mutableStateOf("") }
    var bLat by remember { mutableStateOf("") }
    var bLng by remember { mutableStateOf("") }

    val manualPoint = run {
        val lat = latText.trim().toDoubleOrNull()
        val lng = lngText.trim().toDoubleOrNull()
        if (lat != null && lng != null) LatLng(lat, lng).takeIf { it.isValid() } else null
    }

    val parsedPaste = remember(pasteText) {
        if (pasteText.isBlank()) null else GeocodingService.parseCoordinate(pasteText)
    }

    val pointA = run {
        val lat = aLat.trim().toDoubleOrNull()
        val lng = aLng.trim().toDoubleOrNull()
        if (lat != null && lng != null) LatLng(lat, lng).takeIf { it.isValid() } else null
    }
    val pointB = run {
        val lat = bLat.trim().toDoubleOrNull()
        val lng = bLng.trim().toDoubleOrNull()
        if (lat != null && lng != null) LatLng(lat, lng).takeIf { it.isValid() } else null
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings[K.coord_tools_title],
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 32.dp),
        ) {
            // ---- Manual entry ----
            SectionHeader(strings[K.go_to_coordinate])
            GroupCard {
                Column(modifier = Modifier.padding(16.dp)) {
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

                    if (manualPoint != null) {
                        Spacer(Modifier.height(14.dp))
                        ResultBlock(
                            label = strings[K.dms_format],
                            value = GeocodingService.toDms(manualPoint),
                            onCopy = {
                                copyToClipboard(context, GeocodingService.toDms(manualPoint))
                                viewModel.postMessage(strings[K.copied])
                            },
                        )
                    } else if (latText.isNotBlank() || lngText.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = strings[K.invalid_coordinate],
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors.danger,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            manualPoint?.let { point ->
                                viewModel.moveCamera(point, 16f)
                                viewModel.selectPoint(point)
                                onUseCoordinate()
                            }
                        },
                        enabled = manualPoint != null,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = strings[K.go_to_coordinate],
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // ---- Paste parsing ----
            SectionHeader(strings[K.parse_paste])
            GroupCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        label = { Text(strings[K.paste_hint], style = MaterialTheme.typography.bodyMedium) },
                        minLines = 2,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (parsedPaste != null) {
                        Spacer(Modifier.height(14.dp))
                        ResultBlock(
                            label = strings[K.decimal_degrees],
                            value = Format.coordPair(parsedPaste),
                            onCopy = {
                                copyToClipboard(context, Format.coordPair(parsedPaste))
                                viewModel.postMessage(strings[K.copied])
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.moveCamera(parsedPaste, 16f)
                                viewModel.selectPoint(parsedPaste)
                                onUseCoordinate()
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = strings[K.use_location],
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else if (pasteText.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = strings[K.invalid_coordinate],
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors.danger,
                        )
                    }
                }
            }

            // ---- Distance between two points ----
            SectionHeader(strings[K.distance_between])
            GroupCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings[K.point_a],
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = aLat,
                            onValueChange = { aLat = it },
                            label = { Text(strings[K.lat], style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = aLng,
                            onValueChange = { aLng = it },
                            label = { Text(strings[K.lng], style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = strings[K.point_b],
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = bLat,
                            onValueChange = { bLat = it },
                            label = { Text(strings[K.lat], style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = bLng,
                            onValueChange = { bLng = it },
                            label = { Text(strings[K.lng], style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (pointA != null && pointB != null) {
                        val distance = pointA.distanceTo(pointB)
                        val bearing = pointA.bearingTo(pointB)
                        Spacer(Modifier.height(16.dp))
                        ResultBlock(
                            label = strings[K.result],
                            value = "${Format.distance(distance, settings.units)}  ·  ${Format.bearing(bearing)}",
                            onCopy = {
                                copyToClipboard(context, Format.distance(distance, settings.units))
                                viewModel.postMessage(strings[K.copied])
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultBlock(
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = appColors.muted,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = value,
                style = MonoValueStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
