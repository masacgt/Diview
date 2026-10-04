package com.kurogane.dividend.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsPrivacyScreen(
    onBack: () -> Unit
) {
    var activeTab by remember { mutableStateOf("privacy") } // "terms" or "privacy"
    var openArt1 by remember { mutableStateOf(true) }
    var openArt2 by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("規約とプライバシー", color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceNavy)
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Security Core Status Badge Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryEmerald)
                            )
                            Text("Kurogane Security Core", color = PrimaryEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(PrimaryEmeraldContainer.copy(alpha = 0.15f))
                                .border(0.5.dp, PrimaryEmeraldContainer.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(10.dp))
                                Text("保護稼働中", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Grid details
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // AES-256 Box
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                            border = BorderStroke(0.5.dp, BorderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("端末内AES-256暗号化", color = OnSurface, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("クラウド送信 0 Byte", color = SoftGray, fontSize = 8.sp)
                                }
                            }
                        }

                        // Anti-tracking Box
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                            border = BorderStroke(0.5.dp, BorderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("第三者提供・売買ゼロ", color = OnSurface, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("認証情報は非保持", color = SoftGray, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Segment Tab Switcher Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BackgroundDark)
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Terms Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTab == "terms") PrimaryEmerald else Color.Transparent)
                        .clickable { activeTab = "terms" },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = null,
                            tint = if (activeTab == "terms") Color.Black else SoftGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "利用規約",
                            color = if (activeTab == "terms") Color.Black else SoftGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Privacy Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTab == "privacy") PrimaryEmerald else Color.Transparent)
                        .clickable { activeTab = "privacy" },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = if (activeTab == "privacy") Color.Black else SoftGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "プライバシー",
                            color = if (activeTab == "privacy") Color.Black else SoftGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tabs content
            if (activeTab == "terms") {
                // TERMS CONTENT
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Kurogane利用規約", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PrimaryEmeraldContainer.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("2025年改訂版", color = PrimaryEmerald, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                "本規約は、合同会社Kurogane Labsが提供するポートフォリオ分析アプリ「Kurogane Dividend」の利用条件を定めるものです。",
                                color = SoftGray,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Article 1
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openArt1 = !openArt1 }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("第1条（総則・本サービスの目的）", color = OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(
                                    imageVector = if (openArt1) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = SoftGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (openArt1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BackgroundDark.copy(alpha = 0.5f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        "本アプリは、ユーザー自身が手動入力またはCSVインポートした保有銘柄、取得価格、保有株数に基づき、年間想定配当額、税引後キャッシュフロー、受取月カレンダーを試算・可視化する個人向けシミュレーションツールです。証券会社IDやパスワードを預からず運用されます。",
                                        color = SoftGray,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Article 2 (Exclusion of liability)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = BorderStroke(1.dp, Color(0xFF991B1B).copy(alpha = 0.3f))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openArt2 = !openArt2 }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("第2条（投資助言に関する免責事項）", color = Color(0xFFFCA5A5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(
                                    imageVector = if (openArt2) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (openArt2) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BackgroundDark.copy(alpha = 0.5f))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "1. 当社は、金融商品取引法に基づく金融商品取引業者（投資助言・代理業等）ではありません。本アプリ内で表示される指標は参考値であり、特定の有価証券の売買推奨を行うものではありません。",
                                        color = SoftGray,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                    Text(
                                        "2. 配当金は企業の財務状況や市場動向により減配・無配となる場合があります。いかなる投資判断の損失についても当社は責任を負いません。",
                                        color = SoftGray,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Article 3
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("第3条（知的財産権および禁止事項）", color = OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "本アプリ内のUIデザイン、グラフィック、アルゴリズムの著作権は当社に帰属します。リバースエンジニアリング、不正クローリング、複製行為は固く禁止いたします。",
                                color = SoftGray,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            } else {
                // PRIVACY CONTENT
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Zero Knowledge Hero Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryEmeraldContainer.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Zero Knowledge Architecture", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    Text("金融データ端末内完結宣言", color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(
                                "Kurogane Dividendは「あなたの蓄財状況を誰にも見せない」ことを思想として設計されています。入力された全株式データ・投資金額・配当キャッシュフローは、クラウドサーバーへ一切送信されず、端末内でのみ演算処理されます。",
                                color = SoftGray,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )

                            // 2x2 Grid details
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                                    border = BorderStroke(0.5.dp, BorderColor)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("外部送信", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("0 Bytes", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                                        Text("保有株・金額の送出なし", color = SoftGray, fontSize = 8.sp)
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                                    border = BorderStroke(0.5.dp, BorderColor)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("保護規格", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("AES-256", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                                        Text("SQLite端末暗号化", color = SoftGray, fontSize = 8.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Security Core details list
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("1. 情報の収集について", color = OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "本アプリは、外部サービスのアナリティクス機能（クラッシュ追跡等）を最小限に抑え、ユーザーが手入力した資産額などの金融重要データを一切収集しません。",
                                color = SoftGray,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("2. データの端末内ストレージ保管", color = OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "ユーザーのポートフォリオ情報は、Androidシステムのセキュリティサンドボックスモデルに基づき、暗号化が施された領域（EncryptedSharedPreferences、Room Database）に保存されます。他アプリからの不正アクセスを強力に防ぎます。",
                                color = SoftGray,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
