package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.kurogane.dividend.security.fragmentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.*
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kurogane.dividend.data.PortfolioExporter
import com.kurogane.dividend.data.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kurogane.dividend.notifications.NotificationKind
import com.kurogane.dividend.notifications.DiviewNotifications
import com.kurogane.dividend.ui.components.rememberNotificationAccess

private val SettingsCard: Color @Composable get() = SurfaceContainerLow
private val SettingsInset: Color @Composable get() = BackgroundDark
private val SettingsBorder: Color @Composable get() = BorderColor
private val SettingsMint: Color @Composable get() = PrimaryEmerald
private val SettingsMuted: Color @Composable get() = OnSurfaceVariant

@Composable
fun SettingsScreen(
    viewModel: DividendViewModel,
    onBack: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToTerms: () -> Unit = {},
    onNavigateToCsv: () -> Unit = {}
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isOledBlack by viewModel.isOledBlack.collectAsStateWithLifecycle()
    val selectedAccent by viewModel.selectedAccent.collectAsStateWithLifecycle()
    var dataTab by rememberSaveable { mutableStateOf("import") }
    var calcBasis by rememberSaveable { mutableStateOf("eval") }
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()
    val taxDeduction = taxRateMode == "net"
    val usTaxDeduction by viewModel.usTaxDeduction.collectAsStateWithLifecycle()
    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val exchangeRate by viewModel.exchangeRate.collectAsStateWithLifecycle()
    val exchangeRateError by viewModel.exchangeRateError.collectAsStateWithLifecycle()
    var targetYield by rememberSaveable { mutableStateOf("4.0") }
    var threshold by rememberSaveable { mutableIntStateOf(5) }
    val maskAtStartup by viewModel.maskAtStartup.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationSettings by viewModel.notificationSettings.collectAsStateWithLifecycle()
    val notificationAccess = rememberNotificationAccess { action ->
        when (action) {
            "price" -> viewModel.setNotificationEnabled(NotificationKind.PRICE, true)
            "dividend" -> viewModel.setNotificationEnabled(NotificationKind.DIVIDEND, true)
            "dividend_change" -> viewModel.setNotificationEnabled(NotificationKind.DIVIDEND_CHANGE, true)
            "test" -> Toast.makeText(context, if (DiviewNotifications(context).test()) context.getString(R.string.settings_message) else context.getString(R.string.settings_message_2), Toast.LENGTH_LONG).show()
        }
    }
    val exportScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var isUpdatingFx by remember { mutableStateOf(false) }
    val csvExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            isExporting = true
            val stocks = viewModel.holdings.value
            exportScope.launch {
                try {
                    val result = runCatchingCancellable { withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(PortfolioExporter.csv(stocks).toByteArray(Charsets.UTF_8)) }
                            ?: error(resources.getString(R.string.settings_label_63))
                    } }
                    Toast.makeText(context, if (result.isSuccess) context.getString(R.string.settings_message_3) else context.getString(R.string.settings_message_4), Toast.LENGTH_LONG).show()
                } finally { isExporting = false }
            }
        }
    }
    val jsonExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            isExporting = true
            val stocks = viewModel.holdings.value
            exportScope.launch {
                try {
                    val result = runCatchingCancellable { withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(PortfolioExporter.json(stocks).toByteArray(Charsets.UTF_8)) }
                            ?: error(resources.getString(R.string.settings_label_63))
                    } }
                    Toast.makeText(context, if (result.isSuccess) context.getString(R.string.settings_message_5) else context.getString(R.string.settings_message_6), Toast.LENGTH_LONG).show()
                } finally { isExporting = false }
            }
        }
    }
    val accents = listOf(
        "emerald" to Color(0xFF10B981), "cyan" to Color(0xFF06B6D4),
        "blue" to Color(0xFF3B82F6), "amber" to AccentAmber,
        "purple" to Color(0xFF8B5CF6)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BackgroundDark),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            SettingsSection(stringResource(R.string.settings_text), Icons.Default.Storage) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(SettingsInset).padding(3.dp)) {
                        Segment(stringResource(R.string.settings_label), dataTab == "import", Modifier.weight(1f)) { dataTab = "import" }
                        Segment(stringResource(R.string.settings_label_2), dataTab == "export", Modifier.weight(1f)) { dataTab = "export" }
                    }
                    if (dataTab == "import") {
                        AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Label(stringResource(R.string.settings_label_3), 9, SettingsMuted)
                            Label(stringResource(R.string.settings_label_4), 8, SettingsMint)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("SBI証券", "楽天証券", "マネックス", "松井証券").forEach { broker ->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(SettingsInset).border(1.dp, SettingsBorder, RoundedCornerShape(6.dp)).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Label(broker, 8, SettingsMint, true)
                                    Label(stringResource(R.string.settings_label_5), 7, SettingsMuted)
                                }
                            }
                        }
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(vertical = 8.dp).clip(RoundedCornerShape(9.dp))
                                .background(SettingsInset).border(1.dp, SettingsMint.copy(alpha = .48f), RoundedCornerShape(9.dp))
                                .clickable(onClick = onNavigateToCsv),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CloudUpload, null, tint = SettingsMint, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.height(3.dp))
                            Label(stringResource(R.string.settings_label_6), 9, OnSurface, true)
                            Label(stringResource(R.string.settings_label_7), 7, SettingsMuted)
                            Spacer(Modifier.height(4.dp))
                            Label(stringResource(R.string.settings_label_8), 8, SettingsMint, true)
                        }
                        AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Label(stringResource(R.string.settings_label_9), 7, SettingsMuted)
                            Label(stringResource(R.string.asset_management_text_6 , viewModel.holdings.collectAsStateWithLifecycle().value.size), 7, SettingsMint)
                        }
                        Label(stringResource(R.string.settings_label_10), 7, SettingsMuted)
                    } else {
                        Label(stringResource(R.string.settings_label_11), 9, SettingsMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton(stringResource(R.string.settings_label_12), Modifier.weight(1f)) { if (!isExporting) csvExporter.launch("Diview-portfolio.csv") }
                            ActionButton(stringResource(R.string.settings_label_13), Modifier.weight(1f)) { if (!isExporting) jsonExporter.launch("Diview-portfolio.json") }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Label(stringResource(R.string.settings_label_14), 7, SettingsMuted)
                }
            }
        }

        item {
            SettingsSection(stringResource(R.string.settings_text_2), Icons.Default.Palette) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label(stringResource(R.string.settings_label_15), 9, OnSurface, true)
                        Label(if (themeMode == "dark") resources.getString(R.string.settings_dark_active) else if (themeMode == "light") resources.getString(R.string.settings_label_59) else resources.getString(R.string.settings_label_60), 8, SettingsMint)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("dark" to stringResource(R.string.settings_label_42), "light" to stringResource(R.string.settings_label_43), "system" to stringResource(R.string.settings_label_44)).forEach { (mode, name) ->
                            Segment(name, themeMode == mode, Modifier.weight(1f)) { viewModel.setThemeMode(mode) }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label(stringResource(R.string.settings_label_16), 9, OnSurface, true)
                        Label(selectedAccent.replaceFirstChar { it.uppercase() }, 8, SettingsMint)
                    }
                    AdaptiveRow(Modifier.fillMaxWidth().padding(horizontal = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        accents.forEach { (name, color) ->
                            val accentLabel = stringResource(when (name) {
                                "cyan" -> R.string.accent_cyan
                                "blue" -> R.string.accent_blue
                                "amber" -> R.string.accent_amber
                                "purple" -> R.string.accent_purple
                                else -> R.string.accent_emerald
                            })
                            Box(
                                Modifier.size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedAccent == name) SettingsMint.copy(alpha = .14f) else SettingsCard)
                                    .border(
                                        if (selectedAccent == name) 2.dp else 1.dp,
                                        if (selectedAccent == name) SettingsMint else SettingsMuted.copy(alpha = .75f),
                                        CircleShape
                                    )
                                    .selectable(selectedAccent == name, role = Role.RadioButton, onClick = { viewModel.setSelectedAccent(name) })
                                    .semantics { contentDescription = accentLabel },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(25.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                                    if (selectedAccent == name) Icon(Icons.Default.Check, null, tint = OnSurface, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle(stringResource(R.string.settings_text_3), stringResource(R.string.settings_label_45), isOledBlack, { viewModel.setIsOledBlack(it) }, tag = stringResource(R.string.settings_label_46))
                }
            }
        }

        item {
            SettingsSection(stringResource(R.string.settings_text_4), Icons.Default.Tune) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label(stringResource(R.string.settings_label_17), 9, OnSurface, true); Label(stringResource(R.string.settings_label_18), 7, SettingsMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BasisChoice(stringResource(R.string.settings_text_5), stringResource(R.string.settings_label_47), calcBasis == "eval", Modifier.weight(1f)) { calcBasis = "eval" }
                        BasisChoice(stringResource(R.string.settings_text_6), stringResource(R.string.settings_label_48), calcBasis == "cost", Modifier.weight(1f)) { calcBasis = "cost" }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle(stringResource(R.string.settings_text_7), stringResource(R.string.settings_label_49), taxDeduction, { viewModel.setTaxRateMode(if (it) "net" else "gross") })
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle(stringResource(R.string.settings_text_8), stringResource(R.string.settings_label_50), usTaxDeduction, viewModel::setUsTaxDeduction)
                    Label(stringResource(R.string.settings_label_19), 7, SettingsMuted)
                    Label(exchangeRate?.let { resources.getString(R.string.settings_label_61, (String.format(java.util.Locale.JAPAN, "%.2f", it.usdJpy)).toString(), (it.date).toString()) } ?: resources.getString(R.string.settings_label_62), 8, SettingsMuted)
                    exchangeRateError?.let { Label(it, 7, SettingsMuted) }
                    ActionButton(if (isUpdatingFx) stringResource(R.string.fx_loading) else stringResource(R.string.settings_label_20), Modifier.fillMaxWidth(), enabled = !isUpdatingFx) {
                        exportScope.launch {
                            isUpdatingFx = true
                            try { viewModel.refreshExchangeRate() } finally { isUpdatingFx = false }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label(stringResource(R.string.settings_label_21), 9, OnSurface, true); Label(stringResource(R.string.settings_label_22 , String.format(java.util.Locale.JAPAN, "%.2f", targetYield.toDoubleOrNull() ?: 0.0)), 8, SettingsMint) }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("3.5" to stringResource(R.string.settings_label_51), "4.0" to stringResource(R.string.settings_label_52), "4.5" to stringResource(R.string.settings_label_53)).forEach { (value, label) ->
                            Segment(label, targetYield == value, Modifier.weight(1f)) { targetYield = value }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label(stringResource(R.string.settings_label_23), 9, OnSurface, true); Label(stringResource(R.string.settings_1_s , threshold), 8, SettingsMint) }
                    ThresholdSlider(threshold) { threshold = it }
                    AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label(stringResource(R.string.settings_label_24), 7, SettingsMuted); Label(stringResource(R.string.settings_label_25), 7, SettingsMint); Label(stringResource(R.string.settings_label_26), 7, SettingsMuted) }
                }
            }
        }

        item {
            SettingsSection(stringResource(R.string.settings_text_9), Icons.Default.Notifications) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingToggle(stringResource(R.string.settings_text_10), stringResource(R.string.settings_label_54), notificationSettings.dividendEnabled, {
                        if (it) notificationAccess.request("dividend") else viewModel.setNotificationEnabled(NotificationKind.DIVIDEND, false)
                    })
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle(stringResource(R.string.notification_dividend_change_title), stringResource(R.string.notification_dividend_change_summary), notificationSettings.dividendChangeEnabled, {
                        if (it) notificationAccess.request("dividend_change") else viewModel.setNotificationEnabled(NotificationKind.DIVIDEND_CHANGE, false)
                    })
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle(stringResource(R.string.settings_text_11), stringResource(R.string.settings_label_55), notificationSettings.priceEnabled, {
                        if (it) notificationAccess.request("price") else viewModel.setNotificationEnabled(NotificationKind.PRICE, false)
                    })
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Label(stringResource(R.string.settings_label_27), 9, OnSurface, true); Label(stringResource(R.string.settings_label_28), 7, SettingsMuted) }
                        Label(stringResource(R.string.settings_label_29), 8, SettingsMint)
                    }
                    Label(stringResource(R.string.settings_label_30), 7, SettingsMuted)
                    Label(stringResource(R.string.settings_label_31 , if (notificationAccess.dividendAllowed) "ON" else "OFF", if (notificationAccess.priceAllowed) "ON" else "OFF", if (notificationAccess.dividendChangeAllowed) "ON" else "OFF"), 8, SettingsMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(stringResource(R.string.settings_label_32), Modifier.weight(1f)) { notificationAccess.request("test") }
                        ActionButton(stringResource(R.string.settings_label_33), Modifier.weight(1f)) { notificationAccess.openSettings() }
                    }
                }
            }
        }

        item {
            SettingsSection(stringResource(R.string.settings_text_12), Icons.Default.Shield) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingToggle(stringResource(R.string.settings_text_13), stringResource(R.string.settings_label_56), biometricEnabled, { enabled ->
                        val activity = context.fragmentActivity()
                        if (activity is com.kurogane.dividend.MainActivity) activity.authenticatePreference(enabled)
                        else Toast.makeText(context, context.getString(R.string.settings_message_7), Toast.LENGTH_LONG).show()
                    })
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle(stringResource(R.string.settings_text_14), stringResource(R.string.settings_label_57), maskAtStartup, { viewModel.setMaskAtStartup(it) }, tag = stringResource(R.string.settings_label_58))
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Label(stringResource(R.string.settings_label_34), 9, ErrorRed, true); Label(stringResource(R.string.settings_label_35), 7, SettingsMuted) }
                        Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(5.dp)).background(ErrorRed.copy(alpha = .10f)).border(1.dp, ErrorRed.copy(alpha = .45f), RoundedCornerShape(5.dp)).clickable(role = Role.Button) { confirmReset = true }.padding(horizontal = 9.dp, vertical = 5.dp)) { Label(stringResource(R.string.settings_label_36), 8, ErrorRed, true) }
                    }
                }
            }
        }

        item {
            SettingsSection(stringResource(R.string.settings_text_15), Icons.Default.Info) {
                Column {
                    SupportRow(stringResource(R.string.settings_label_37), onNavigateToFeedback)
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SupportRow(stringResource(R.string.settings_label_38), onNavigateToTerms)
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SupportRow(stringResource(R.string.settings_label_39), {})
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    AdaptiveRow(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) { Label(stringResource(R.string.settings_label_40), 8, SettingsMuted); Label(stringResource(R.string.settings_v_1_s_build_2_s , com.kurogane.dividend.BuildConfig.VERSION_NAME, com.kurogane.dividend.BuildConfig.VERSION_CODE), 8, OnSurface, true) }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Label(stringResource(R.string.settings_label_41), 7, SettingsMint)
                Spacer(Modifier.height(3.dp)); Label(stringResource(R.string.settings_2025_diview), 6, SettingsMuted)
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.settings_text_16), color = OnSurface) },
        text = { Text(stringResource(R.string.settings_text_17), color = SettingsMuted) },
        confirmButton = { TextButton(onClick = {
            confirmReset = false
            exportScope.launch {
                val result = runCatchingCancellable { viewModel.resetLocalData() }
                Toast.makeText(context, if (result.isSuccess) context.getString(R.string.settings_message_8) else context.getString(R.string.settings_message_9), Toast.LENGTH_LONG).show()
            }
        }) { Text(stringResource(R.string.settings_label_36), color = ErrorRed) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel), color = SettingsMuted) } },
        containerColor = SettingsCard
    )
}

@Composable
private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(start = 3.dp)) {
            Icon(icon, null, tint = SettingsMint, modifier = Modifier.size(14.dp))
            Text(title, color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SettingsCard).border(.7.dp, SettingsBorder, RoundedCornerShape(10.dp)).padding(10.dp), content = content)
    }
}

@Composable
private fun Label(text: String, size: Int, color: Color, bold: Boolean = false) {
    Text(text, color = color, fontSize = (size + 3).sp, lineHeight = (size + 6).sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
}

@Composable
private fun Segment(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) SettingsMint.copy(alpha = .16f) else SettingsCard)
            .border(1.dp, if (selected) SettingsMint else SettingsMuted.copy(alpha = .75f), shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) SettingsMint else SettingsMuted,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(text, color = if (selected) SettingsMint else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, onChanged: (Boolean) -> Unit, tag: String? = null, enabled: Boolean = true) {
    AdaptiveRow(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChanged), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(title, 8, OnSurface, true)
                if (tag != null) Box(Modifier.clip(RoundedCornerShape(3.dp)).background(SettingsMint.copy(alpha = .13f)).padding(horizontal = 4.dp, vertical = 1.dp)) { Label(tag, 6, SettingsMint, true) }
            }
            Label(detail, 7, SettingsMuted)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(31.dp).height(17.dp).clip(CircleShape).background(if (checked) PrimaryEmeraldContainer else SurfaceContainerHighest).padding(2.dp), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(13.dp).clip(CircleShape).background(OnSurface))
        }
    }
}

@Composable
private fun BasisChoice(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(7.dp)).background(if (selected) SettingsMint.copy(alpha = 0.14f) else SettingsInset).border(1.dp, if (selected) SettingsMint else SettingsBorder, RoundedCornerShape(7.dp)).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(7.dp)) {
        AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label(title, 8, if (selected) OnSurface else SettingsMuted, true)
            Icon(if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null, tint = if (selected) SettingsMint else SettingsMuted, modifier = Modifier.size(12.dp))
        }
        Label(subtitle, 7, SettingsMuted)
    }
}

@Composable
private fun ActionButton(label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(7.dp)).background(SettingsInset).border(1.dp, SettingsBorder, RoundedCornerShape(7.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(6.dp), contentAlignment = Alignment.Center) { Label(label, 8, OnSurface, true) }
}

@Composable
private fun SupportRow(label: String, onClick: () -> Unit) {
    AdaptiveRow(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Label(label, 8, OnSurface, true)
        Icon(Icons.Default.ChevronRight, null, tint = SettingsMuted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun ThresholdSlider(value: Int, onValueChange: (Int) -> Unit) {
    val changeValue by rememberUpdatedState(onValueChange)
    val label = stringResource(R.string.settings_label_23)
    val trackColor = SurfaceContainerHighest
    val accentColor = PrimaryEmeraldContainer
    Canvas(Modifier.fillMaxWidth().height(48.dp).semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 3f..10f, 6)
        setProgress { requested ->
            if (!requested.isFinite()) false else { changeValue(requested.roundToInt().coerceIn(3, 10)); true }
        }
    }.pointerInput(Unit) {
        detectTapGestures { changeValue((3 + (it.x / size.width * 7).roundToInt()).coerceIn(3, 10)) }
    }.pointerInput(Unit) {
        detectDragGestures { change, _ -> change.consume(); changeValue((3 + (change.position.x / size.width * 7).roundToInt()).coerceIn(3, 10)) }
    }) {
        val y = size.height / 2f
        val x = (value - 3) / 7f * size.width
        drawLine(trackColor, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(accentColor, Offset(0f, y), Offset(x, y), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(accentColor, 6.dp.toPx(), Offset(x, y))
    }
}
