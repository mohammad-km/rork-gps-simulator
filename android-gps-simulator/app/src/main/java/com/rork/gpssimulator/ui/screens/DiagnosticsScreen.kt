package com.rork.gpssimulator.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.data.model.MockReadiness
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.components.DiagnosticRow
import com.rork.gpssimulator.ui.components.GroupCard
import com.rork.gpssimulator.ui.components.RowDivider
import com.rork.gpssimulator.ui.components.SectionHeader
import com.rork.gpssimulator.ui.theme.LocalAppColors
import com.rork.gpssimulator.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val report by viewModel.diagnostics.collectAsState()
    val isRunning by viewModel.isDiagnosticsRunning.collectAsState()
    val settings by viewModel.settings.collectAsState()

    // Run once when the screen opens so values are never stale.
    LaunchedEffect(Unit) {
        if (report == null) viewModel.runDiagnostics()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings[K.diagnostics_title],
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
                .padding(bottom = 32.dp),
        ) {
            Button(
                onClick = { viewModel.runDiagnostics() },
                enabled = !isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(58.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = strings[K.running_diagnostic],
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings[K.run_diagnostic],
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            val current = report

            SectionHeader(strings[K.system_checks])
            GroupCard {
                DiagnosticRow(
                    label = strings[K.real_gps_status],
                    value = if (current?.realGpsAvailable == true) {
                        strings[K.available]
                    } else {
                        strings[K.unavailable]
                    },
                    dotColor = if (current?.realGpsAvailable == true) appColors.success else appColors.danger,
                )
                RowDivider()
                DiagnosticRow(
                    label = strings[K.permission_status],
                    value = if (current?.permissionGranted == true) {
                        strings[K.granted]
                    } else {
                        strings[K.denied]
                    },
                    dotColor = if (current?.permissionGranted == true) appColors.success else appColors.danger,
                )
                RowDivider()
                DiagnosticRow(
                    label = strings[K.mock_status],
                    value = when (current?.mockReadiness) {
                        MockReadiness.READY -> strings[K.mock_ready]
                        MockReadiness.SETUP_REQUIRED -> strings[K.mock_setup_required]
                        MockReadiness.PERMISSION_REQUIRED -> strings[K.mock_permission_required]
                        MockReadiness.DEV_OPTIONS_REQUIRED -> strings[K.mock_dev_options_required]
                        null -> "—"
                    },
                    dotColor = when (current?.mockReadiness) {
                        MockReadiness.READY -> appColors.success
                        null -> appColors.muted
                        else -> appColors.warning
                    },
                )
                RowDivider()
                DiagnosticRow(
                    label = strings[K.gps_enabled],
                    value = if (current?.gpsEnabled == true) strings[K.enabled] else strings[K.disabled],
                    dotColor = if (current?.gpsEnabled == true) appColors.success else appColors.warning,
                )
                RowDivider()
                DiagnosticRow(
                    label = strings[K.location_provider],
                    value = current?.providerName ?: "—",
                    dotColor = if (current?.providerName != null && current.providerName != "Unavailable") {
                        appColors.success
                    } else {
                        appColors.warning
                    },
                )
                RowDivider()
                DiagnosticRow(
                    label = strings[K.network],
                    value = if (current?.networkAvailable == true) {
                        strings[K.available]
                    } else {
                        strings[K.offline]
                    },
                    dotColor = if (current?.networkAvailable == true) appColors.success else appColors.warning,
                )
            }

            SectionHeader(strings[K.live_values])
            GroupCard {
                val sample = current?.sample
                DiagnosticRow(
                    label = "${strings[K.lat]}",
                    value = sample?.let { Format.coord(it.point.lat) } ?: "—",
                )
                RowDivider()
                DiagnosticRow(
                    label = "${strings[K.lng]}",
                    value = sample?.let { Format.coord(it.point.lng) } ?: "—",
                )
                RowDivider()
                DiagnosticRow(
                    label = strings[K.accuracy],
                    value = sample?.let {
                        Format.shortDistance(it.accuracy.toDouble(), settings.units)
                    } ?: "—",
                )
                RowDivider()
                DiagnosticRow(
                    label = strings[K.altitude],
                    value = sample?.let {
                        Format.shortDistance(it.altitude, settings.units)
                    } ?: "—",
                )
                RowDivider()
                DiagnosticRow(
                    label = strings[K.speed],
                    value = sample?.let {
                        Format.speedFromMetersPerSecond(it.speed, settings.units)
                    } ?: "—",
                )
                RowDivider()
                DiagnosticRow(
                    label = strings[K.bearing],
                    value = sample?.let { Format.bearing(it.bearing) } ?: "—",
                )
                RowDivider()
                DiagnosticRow(
                    label = strings[K.last_update],
                    value = sample?.let { formatAge(it.timestamp, strings[K.just_now], strings[K.seconds_ago]) }
                        ?: strings[K.never],
                )
            }
        }
    }
}

private fun formatAge(timestamp: Long, justNow: String, secondsAgo: String): String {
    val delta = (System.currentTimeMillis() - timestamp).coerceAtLeast(0)
    val seconds = delta / 1000
    return when {
        seconds < 2 -> justNow
        seconds < 90 -> "$seconds$secondsAgo"
        else -> Format.clockTime(timestamp)
    }
}
