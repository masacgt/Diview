package com.kurogane.dividend.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
fun CsvImportScreen(
    onBack: () -> Unit = {}
) {
    var step by remember { mutableIntStateOf(1) } // 1: 証券会社選択・ファイル, 2: 差分照合プレビュー, 3: 取込完了
    var selectedBroker by remember { mutableStateOf("sbi") }

    val brokers = listOf(
        "sbi" to "SBI証券",
        "rakuten" to "楽天証券",
        "monex" to "マネックス証券",
        "matsui" to "松井証券",
        "moomoo" to "moomoo証券"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "証券CSVインポート",
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
            // Wizard Stepper Indicator
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainerHigh)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepBadge(1, "証券選択", isActive = step == 1, isCompleted = step > 1)
                    Divider(modifier = Modifier.width(20.dp), color = OnSurfaceVariant)
                    StepBadge(2, "差分プレビュー", isActive = step == 2, isCompleted = step > 2)
                    Divider(modifier = Modifier.width(20.dp), color = OnSurfaceVariant)
                    StepBadge(3, "インポート完了", isActive = step == 3, isCompleted = false)
                }
            }

            if (step == 1) {
                item {
                    Text("1. インポート元の証券会社を選択", style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        brokers.forEach { (id, name) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedBroker = id },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedBroker == id) SurfaceContainerHighest else SurfaceContainer
                                ),
                                border = if (selectedBroker == id) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PrimaryEmerald)) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    if (selectedBroker == id) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryEmerald)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    // Upload Dropzone
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { step = 2 },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("CSVファイルを選択またはドロップ", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            Text("タップしてデモCSVファイルを読み込む", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant))
                        }
                    }
                }
            }

            if (step == 2) {
                item {
                    Text("2. 取込対象の差分検出結果", style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    Text("既存のポートフォリオとの重複と新規銘柄を自動解析しました。", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant))
                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("7203 トヨタ自動車", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("既存合算 (+100株)", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                            }
                            Divider(color = SurfaceContainerHighest)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("8316 三井住友FG", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("新規追加 (100株)", color = SkyBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { step = 3 },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("インポートを実行する", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (step == 3) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("インポート完了！", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                            Text("保有資産リストと配当金スケジュールが更新されました。", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant))
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("資産管理画面へ戻る", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBadge(stepNum: Int, label: String, isActive: Boolean, isCompleted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isCompleted || isActive) PrimaryEmerald else SurfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isCompleted) "✓" else "$stepNum",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isCompleted || isActive) Color.Black else OnSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isActive) Color.White else OnSurfaceVariant,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
            )
        )
    }
}

