package com.kurogane.dividend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.model.AccentColor
import com.kurogane.dividend.model.ThemeMode
import com.kurogane.dividend.viewmodel.DividendViewModel
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DividendViewModel,
    onBack: () -> Unit = {},
    onContact: () -> Unit = {},
    onTerms: () -> Unit = {},
    onCsvImport: () -> Unit = {}
) {
    val uiSettings by viewModel.uiSettings.collectAsState()
    val context = LocalContext.current
    var calcBasis by remember { mutableStateOf("eval") }
    var taxDeduction by remember { mutableStateOf(true) }
    var usTaxDeduction by remember { mutableStateOf(true) }
    var targetYield by remember { mutableFloatStateOf(4f) }
    var notifyExDiv by remember { mutableStateOf(true) }
    var notifyBuySignal by remember { mutableStateOf(true) }
    var biometrics by remember { mutableStateOf(false) }
    var maskAtStartup by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "設定・データ管理",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CSV & Backup Section
            item {
                var backupMode by remember { mutableStateOf("import") }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("データ管理＆バックアップ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background).padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("import" to "インポート (取込)", "export" to "エクスポート (保存)").forEach { (mode, label) ->
                                    Surface(
                                        modifier = Modifier.weight(1f).clickable { backupMode = mode },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (backupMode == mode) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else Color.Transparent,
                                        border = if (backupMode == mode) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                                    ) {
                                        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (mode == "import") Icons.Default.UploadFile else Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                            Text("対応証券口座フォーマット", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("SBI証券", "楽天証券", "マネックス", "moomoo").forEach { broker ->
                                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.background) {
                                        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(broker, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text("CSV取込", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (backupMode == "import") onCsvImport()
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.background,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .55f))
                            ) {
                                Column(Modifier.padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(if (backupMode == "import") Icons.Default.CloudUpload else Icons.Default.SaveAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (backupMode == "import") "CSVファイルを選択 または ウィザードを開く" else "端末データをCSVとして保存",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(if (backupMode == "import") "UTF-8 / Shift-JIS自動検知 (最大10MB)" else "保有資産・Watchlist・設定を保存", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("ⓘ 前回インポート:", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("2024/10/24 18:30", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.width(6.dp))
                                    Text("SBI証券 48銘柄", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text("ⓘ 各証券会社CSVのダウンロード手順　保存データへ上書き / 追加", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                            HorizontalDivider()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("CSVファイルは端末内でのみ処理され、外部送信されません。", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Theme Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "外観・テーマ設定",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Dark / Light / System
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                ThemeMode.DARK to "ダーク",
                                ThemeMode.LIGHT to "ライト",
                                ThemeMode.SYSTEM to "システム連動"
                            ).forEach { (mode, label) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (uiSettings.themeMode == mode) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { viewModel.setThemeMode(mode) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (uiSettings.themeMode == mode) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        // Accent Color
                        Text(
                            "アクセントカラー",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val colors = listOf(
                            AccentColor.EMERALD to Color(0xFF10B981),
                            AccentColor.CYAN to Color(0xFF06B6D4),
                            AccentColor.BLUE to Color(0xFF3B82F6),
                            AccentColor.AMBER to Color(0xFFF59E0B),
                            AccentColor.PURPLE to Color(0xFF8B5CF6)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            colors.forEach { (name, color) ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (uiSettings.accentColor == name) 3.dp else 0.dp,
                                            color = if (uiSettings.accentColor == name) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setAccentColor(name) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // OLED Black Switch follows accent selection in the reference settings screen.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "有機EL 完全漆黒モード (OLED Pure Black)",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                                )
                                Text(
                                    "背景を純黒(#000000)にし、ディスプレイ消費電力を大幅低減",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                )
                            }
                            Switch(
                                checked = uiSettings.oledBlack,
                                onCheckedChange = viewModel::setOledBlack,
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection("表示・計算カスタマイズ", Icons.Default.Tune) {
                    Text("配当利回りの計算基準", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("eval" to "評価額基準\n現在値ベース", "cost" to "取得額基準\n買付単価 (YOC)").forEach { (key, label) ->
                            Surface(Modifier.weight(1f).clickable { calcBasis = key }, color = if (calcBasis == key) SurfaceContainerHighest else MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (calcBasis == key) MaterialTheme.colorScheme.primary else Color(0xFF26334A))) {
                                Text(label, Modifier.padding(10.dp), color = Color.White, fontSize = 10.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                    HorizontalDivider()
                    SettingsToggle("税金計算設定", "税引後表示（国内税20.315%を控除）", taxDeduction) { taxDeduction = it }
                    HorizontalDivider()
                    SettingsToggle("米国株 現地源泉徴収控除 (10%)", "外国税額控除前の手取り概算に反映", usTaxDeduction) { usTaxDeduction = it }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("目標利回り (ロードマップ試算)", color = Color.White, fontSize = 11.sp); Text("${String.format("%.1f", targetYield)}%", color = PrimaryEmerald, fontWeight = FontWeight.Bold) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3.5f, 4f, 4.5f).forEach { value -> FilterChip(selected = targetYield == value, onClick = { targetYield = value }, label = { Text("${String.format("%.1f", value)}%", fontSize = 10.sp) }) }
                    }
                }
            }
            item {
                SettingsSection("通知・リマインダー設定", Icons.Default.Notifications) {
                    SettingsToggle("配当金の権利落ち日・入金通知", "権利付最終日および振込予定日をプッシュ通知", notifyExDiv) { notifyExDiv = it }
                    HorizontalDivider()
                    SettingsToggle("買場シグナル・株価アラート", "利回り4.5%突破時や年初来安値更新時", notifyBuySignal) { notifyBuySignal = it }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("通知時間帯の指定", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold); Text("市場寄り前・引け後まとめ", color = OnSurfaceVariant, fontSize = 9.sp) }
                        Text("朝 08:30 / 夕 17:30", color = PrimaryEmerald, fontSize = 10.sp)
                    }
                }
            }
            item {
                SettingsSection("セキュリティ＆プライバシー", Icons.Default.Shield) {
                    SettingsToggle("生体認証 (Face ID / 指紋認証)", "アプリ起動時のロック解除", biometrics) { biometrics = it }
                    HorizontalDivider()
                    SettingsToggle("起動時の金額伏字マスク", "電車内など外出時の画面閲覧用", maskAtStartup) { maskAtStartup = it }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("ローカルデータ完全消去", color = Color(0xFFFF9898), fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("端末内の保有・設定データをすべて削除", color = OnSurfaceVariant, fontSize = 9.sp) }
                        OutlinedButton(onClick = { confirmClear = true }, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9898)), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) { Text("初期化", color = Color(0xFFFF9898), fontSize = 10.sp) }
                    }
                }
            }

            item {
                Text(
                    "アプリ情報・サポート",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        SettingsLinkRow("お問い合わせ・フィードバック", Icons.Default.ChevronRight, onContact)
                        HorizontalDivider()
                        SettingsLinkRow("利用規約＆プライバシーポリシー", Icons.Default.ChevronRight, onTerms)
                        HorizontalDivider()
                        SettingsLinkRow("アプリをレビューで応援", Icons.Default.OpenInNew) {
                            val url = "https://play.google.com/store/apps/details?id=com.kurogane.dividend"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("バージョン")
                            Text("v1.2.0 (Build 248)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("ローカルデータを初期化しますか？") },
        text = { Text("端末に保存された保有銘柄とWatchlistを削除します。この操作は取り消せません。") },
        confirmButton = { TextButton(onClick = { viewModel.clearLocalData(); confirmClear = false }) { Text("削除する", color = Color(0xFFFF9898)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("キャンセル") } },
        containerColor = SurfaceContainer
    )
}

@Composable
private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainer), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = OnSurfaceVariant, fontSize = 9.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsLinkRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
