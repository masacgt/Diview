package com.kurogane.dividend.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.viewmodel.DividendViewModel

// Local High-Fidelity Color Theme matching React Web App CSS
private val SpaceNavy = Color(0xFF0B1326)
private val CardDark = Color(0xFF131B2E)
private val NeonGreen = Color(0xFF10B981)
private val NeonGreenLight = Color(0xFF4EDEA3)
private val SoftGray = Color(0xFFBBCABF)
private val BorderBlue = Color(0xFF1E293B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetManagementScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {}
) {
    var isPrivacyActive by remember { mutableStateOf(false) }
    
    // Dialog / Modal State
    var showAddAssetModal by remember { mutableStateOf(false) }
    var stockCode by remember { mutableStateOf("") }
    var shareCount by remember { mutableStateOf("") }
    var acqPrice by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Left Mini Icon Badge
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF172033))
                                .border(1.dp, Color(0x3310B981), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Title Column
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "資産管理",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Compose badge from React Header
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x2210B981))
                                        .border(0.5.dp, Color(0x4410B981), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "🤖 Compose",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = NeonGreen
                                    )
                                }
                            }
                            Text(
                                "ASSET MANAGEMENT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoftGray,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = SoftGray)
                    }
                    Box(contentAlignment = Alignment.TopEnd) {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = SoftGray)
                        }
                        // Notification Green Active Dot
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp, end = 10.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NeonGreen)
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = SoftGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SpaceNavy
                )
            )
        },
        floatingActionButton = {
            // Glowing Plus FAB triggers the Add Modal
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp, end = 4.dp)
                    .size(60.dp)
                    .shadow(16.dp, CircleShape, spotColor = NeonGreen, ambientColor = NeonGreen)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NeonGreenLight, NeonGreen)
                        )
                    )
                    .clickable { showAddAssetModal = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Asset",
                    tint = Color(0xFF031C13),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        containerColor = SpaceNavy
    ) { innerPadding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            
            // ================= STATE SIMULATOR BAR =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11192C)),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(NeonGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "STATE SIMULATOR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonGreenLight
                            )
                        }
                        Text(
                            "画面状態の動的切替",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoftGray
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Simulated capsules
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Capsule 1 (Active)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeonGreen)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "① 通常・一覧",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }

                        // Capsule 2 (Inactive)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A2338))
                                .border(0.5.dp, BorderBlue, RoundedCornerShape(12.dp))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = SoftGray,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "② オフライン",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SoftGray
                                )
                            }
                        }

                        // Capsule 3 (Inactive)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A2338))
                                .border(0.5.dp, BorderBlue, RoundedCornerShape(12.dp))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = SoftGray,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "③ 認証状態...",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SoftGray
                                )
                            }
                        }
                    }
                }
            }

            // ================= TOTAL ASSET HERO CARD =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "総資産評価額",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoftGray
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                if (isPrivacyActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Privacy",
                                tint = SoftGray,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { isPrivacyActive = !isPrivacyActive }
                            )
                        }

                        // Glow Profit pill
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x1F10B981))
                                .border(0.5.dp, Color(0x3310B981), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "+¥56,888 (+0.0%)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonGreen
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Huge valuation text matching screenshot
                    Text(
                        text = if (isPrivacyActive) "¥ •,•••,•••,•••" else "¥1,223,598,800",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "投資元本: ¥1,223,541,912",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SoftGray
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Divider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Sub Cards Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 1: Expected Dividend
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0C1322))
                                .border(0.5.dp, BorderBlue, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    "年間予想受取配当",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SoftGray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "¥8,820 /年",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonGreen
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "税引後概算: ¥7,028",
                                    fontSize = 10.sp,
                                    color = SoftGray
                                )
                            }
                        }

                        // Card 2: Yield Rate
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0C1322))
                                .border(0.5.dp, BorderBlue, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    "平均配当利回り",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SoftGray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        "0.00",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        "%",
                                        fontSize = 10.sp,
                                        color = SoftGray,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                // Good dividend level badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x1110B981))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NeonGreen,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        "好配当水準",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = NeonGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================= MONTHLY CHART CARD =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "月別推移グラフ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1C253B))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "2024",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SoftGray
                                )
                            }
                        }
                        Text(
                            "年間合計: ¥124,500",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonGreenLight
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 1-12 Custom Monthly Bars with float labels exactly like the image
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val monthlyValues = listOf(
                            Pair(1, "0.2万"), Pair(2, "0.4万"), Pair(3, "1.2万"),
                            Pair(4, "0.2万"), Pair(5, "0.4万"), Pair(6, "3.8万"),
                            Pair(7, "0.2万"), Pair(8, "0.3万"), Pair(9, "1.0万"),
                            Pair(10, "0.2万"), Pair(11, "0.5万"), Pair(12, "4.2万")
                        )

                        monthlyValues.forEach { (month, label) ->
                            // Calculate heights
                            val isSpecialMonth = (month == 6 || month == 12)
                            val barWeight = if (isSpecialMonth) 1.2f else 1f
                            val heightFraction = when (month) {
                                6 -> 0.75f
                                12 -> 0.85f
                                3 -> 0.45f
                                9 -> 0.40f
                                11 -> 0.28f
                                2, 5 -> 0.25f
                                8 -> 0.22f
                                else -> 0.18f
                            }

                            Column(
                                modifier = Modifier
                                    .weight(barWeight)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                // Floating label text
                                Text(
                                    text = label,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSpecialMonth) NeonGreenLight else Color(0xFF00B4D8),
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))

                                // Rounded custom bar
                                Box(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .fillMaxHeight(heightFraction)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (isSpecialMonth) {
                                                Brush.verticalGradient(
                                                    colors = listOf(NeonGreenLight, NeonGreen)
                                                )
                                            } else {
                                                Brush.verticalGradient(
                                                    colors = listOf(Color(0xFF00B4D8), Color(0xFF0077B6))
                                                )
                                            }
                                        )
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Month number label
                                Text(
                                    text = month.toString(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSpecialMonth) Color.White else SoftGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ================= HIGH FIDELITY ASSET ADDITION DIALOG (MODAL) =================
    if (showAddAssetModal) {
        AlertDialog(
            onDismissRequest = { showAddAssetModal = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (stockCode.isNotEmpty() && shareCount.isNotEmpty() && acqPrice.isNotEmpty()) {
                            val shares = shareCount.toIntOrNull() ?: 100
                            val price = acqPrice.toDoubleOrNull() ?: 2500.0
                            viewModel.updateHolding(stockCode, shares, price)
                            
                            // Reset state & close
                            showAddAssetModal = false
                            stockCode = ""
                            shareCount = ""
                            acqPrice = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "登録する",
                        color = Color(0xFF031C13),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddAssetModal = false }
                ) {
                    Text(
                        "キャンセル",
                        color = SoftGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "銘柄の新規登録・修正",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "保有している配当銘柄の情報を入力してください。ダッシュボードに即時反映されます。",
                        color = SoftGray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    
                    // Stock Code Input
                    OutlinedTextField(
                        value = stockCode,
                        onValueChange = { stockCode = it },
                        label = { Text("銘柄コード (例: 7203)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Share Count Input
                    OutlinedTextField(
                        value = shareCount,
                        onValueChange = { shareCount = it },
                        label = { Text("保有株数 (例: 100)", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Acquisition Price Input
                    OutlinedTextField(
                        value = acqPrice,
                        onValueChange = { acqPrice = it },
                        label = { Text("取得単価 (例: 2450)", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            containerColor = CardDark,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
        )
    }
}
