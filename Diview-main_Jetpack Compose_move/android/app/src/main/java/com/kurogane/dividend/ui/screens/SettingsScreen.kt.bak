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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {}
) {
    var themeMode by remember { mutableStateOf("dark") }
    var isOledBlack by remember { mutableStateOf(false) }
    var selectedAccent by remember { mutableStateOf("emerald") }
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
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
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "外観テーマ (Appearance)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Dark / Light / System
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerHigh)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("dark" to "ダーク", "light" to "ライト", "system" to "システム連動").forEach { (mode, label) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (themeMode == mode) PrimaryEmerald else Color.Transparent)
                                        .clickable { themeMode = mode }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (themeMode == mode) Color.Black else OnSurfaceVariant,
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
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                )
                                Text(
                                    "背景を純黒(#000000)にし、ディスプレイ消費電力を大幅低減",
                                    style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, fontSize = 10.sp)
                                )
                            }
                            Switch(
                                checked = isOledBlack,
                                onCheckedChange = { isOledBlack = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = PrimaryEmerald)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Accent Color
                        Text(
                            "アクセントカラー",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val colors = listOf(
                            "emerald" to Color(0xFF10B981),
                            "cyan" to Color(0xFF06B6D4),
                            "blue" to Color(0xFF3B82F6),
                            "amber" to Color(0xFFF59E0B),
                            "purple" to Color(0xFF8B5CF6)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            colors.forEach { (name, color) ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selectedAccent == name) 3.dp else 0.dp,
                                            color = if (selectedAccent == name) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedAccent = name }
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
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "配当計算・税制設定",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
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
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                )
                                Text(
                                    "税引前(グロス) または 特定口座源泉徴収後(20.315%)",
                                    style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, fontSize = 10.sp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainerHigh)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (taxRateMode == "gross") PrimaryEmerald else Color.Transparent)
                                        .clickable { taxRateMode = "gross" }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "税引前",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (taxRateMode == "gross") Color.Black else OnSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (taxRateMode == "net") PrimaryEmerald else Color.Transparent)
                                        .clickable { taxRateMode = "net" }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "手取り",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (taxRateMode == "net") Color.Black else OnSurfaceVariant,
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = PrimaryEmerald)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "データ連携・CSVインポート",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "主要ネット証券 (SBI証券 / 楽天証券 / マネックス証券 / 松井証券 / moomoo証券) の保有資産CSVを取り込み、自動で保有銘柄と配当権利日を登録できます。",
                            style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant, lineHeight = 18.sp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, tint = PrimaryEmerald)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("証券CSVインポートウィザードを開く", color = Color.White)
                        }
                    }
                }
            }

            // Zero Knowledge Security Declaration
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryEmerald)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Zero Knowledge 端末内完結保証",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "本アプリは資産情報・保有株数・取得金額を一切外部サーバーに送信しません。すべての財務計算および暗号化保管はAndroid端末内 (Android Keystore + Room/EncryptedSharedPreferences) で完結します。",
                            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, lineHeight = 16.sp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
