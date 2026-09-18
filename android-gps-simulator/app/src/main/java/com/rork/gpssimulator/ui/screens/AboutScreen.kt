package com.rork.gpssimulator.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.BuildConfig
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.components.DiagnosticRow
import com.rork.gpssimulator.ui.components.GroupCard
import com.rork.gpssimulator.ui.components.InfoPill
import com.rork.gpssimulator.ui.components.RowDivider
import com.rork.gpssimulator.ui.components.SectionHeader
import com.rork.gpssimulator.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: BuildConfig.VERSION_NAME
    }
    val versionCode = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }.getOrNull() ?: BuildConfig.VERSION_CODE
    }
    val providerName = remember(settings.usePlayServices) { viewModel.providerName() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = strings[K.about], style = MaterialTheme.typography.headlineSmall)
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
            // App identity
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(22.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = strings[K.app_name],
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoPill(text = "${strings[K.version]} $versionName")
                    InfoPill(text = strings[K.plan_free], color = appColors.muted)
                }
            }

            GroupCard {
                DiagnosticRow(label = strings[K.version], value = versionName)
                RowDivider()
                DiagnosticRow(label = strings[K.build_number], value = versionCode.toString())
                RowDivider()
                DiagnosticRow(label = strings[K.map_provider], value = "OpenStreetMap")
                RowDivider()
                DiagnosticRow(label = strings[K.location_provider_label], value = providerName)
            }

            SectionHeader(strings[K.check_setup])
            GroupCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings[K.gps_diagnostics_desc],
                        style = MaterialTheme.typography.bodyLarge,
                        color = appColors.muted,
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onOpenDiagnostics,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = strings[K.check_setup],
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            SectionHeader(strings[K.local_data_info])
            GroupCard {
                Text(
                    text = strings[K.local_data_body],
                    style = MaterialTheme.typography.bodyLarge,
                    color = appColors.muted,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = strings[K.pro_coming_soon],
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            )
        }
    }
}

/** Static attribution for the third-party data and libraries the app uses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current

    val entries = listOf(
        Triple(
            "OpenStreetMap",
            "Map tiles and place search (Nominatim)",
            "© OpenStreetMap contributors — Open Database License (ODbL)",
        ),
        Triple(
            "Esri World Imagery",
            "Satellite map tiles",
            "© Esri, Maxar, Earthstar Geographics",
        ),
        Triple(
            "OpenTopoMap",
            "Terrain map tiles",
            "© OpenTopoMap — CC-BY-SA 3.0",
        ),
        Triple(
            "Jetpack Compose & AndroidX",
            "UI toolkit and platform libraries",
            "© The Android Open Source Project — Apache License 2.0",
        ),
        Triple(
            "Kotlin & kotlinx.serialization",
            "Language and data serialization",
            "© JetBrains — Apache License 2.0",
        ),
        Triple(
            "Ktor",
            "HTTP client used for place search",
            "© JetBrains — Apache License 2.0",
        ),
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings[K.open_source_licenses],
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
            entries.forEach { (name, usage, license) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = usage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = license,
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors.muted,
                    )
                }
            }
        }
    }
}
