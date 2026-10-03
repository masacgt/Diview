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
    onBack: () -> Unit = {}
) {
    val uiSettings by viewModel.uiSettings.collectAsState()
    val context = LocalContext.current
    var taxRateMode by remember { mutableStateOf("gross") } // gross or net (20.315%)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "設定・環境",
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // OLED Black Switch
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

                        Spacer(modifier = Modifier.height(16.dp))

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
                    }
                }
            }

            // Calculation and Tax Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "配当計算・税制設定",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "配当表示基準",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                                )
                                Text(
                                    "税引前(グロス) または 特定口座源泉徴収後(20.315%)",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (taxRateMode == "gross") MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { taxRateMode = "gross" }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "税引前",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (taxRateMode == "gross") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (taxRateMode == "net") MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { taxRateMode = "net" }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "手取り",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (taxRateMode == "net") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                                modifier = Modifier.fillMaxWidth().clickable { },
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

            // Zero Knowledge Security Declaration
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Zero Knowledge 端末内完結保証",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "本アプリは資産情報・保有株数・取得金額を一切外部サーバーに送信しません。すべての財務計算および暗号化保管はAndroid端末内 (Android Keystore + Room/EncryptedSharedPreferences) で完結します。",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                        )
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
                        SettingsLinkRow("お問い合わせ・フィードバック", Icons.Default.ChevronRight) { }
                        HorizontalDivider()
                        SettingsLinkRow("利用規約＆プライバシーポリシー", Icons.Default.ChevronRight) { }
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
