package com.kurogane.dividend.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlin.math.roundToInt

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
    val themeMode by viewModel.themeMode.collectAsState()
    val isOledBlack by viewModel.isOledBlack.collectAsState()
    val selectedAccent by viewModel.selectedAccent.collectAsState()
    var dataTab by rememberSaveable { mutableStateOf("import") }
    var calcBasis by rememberSaveable { mutableStateOf("eval") }
    var taxDeduction by rememberSaveable { mutableStateOf(true) }
    var usTaxDeduction by rememberSaveable { mutableStateOf(true) }
    var targetYield by rememberSaveable { mutableStateOf("4.0") }
    var threshold by rememberSaveable { mutableIntStateOf(5) }
    var notifyExDiv by rememberSaveable { mutableStateOf(true) }
    var notifyBuySignal by rememberSaveable { mutableStateOf(true) }
    var biometrics by rememberSaveable { mutableStateOf(true) }
    var maskAtStartup by rememberSaveable { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
            SettingsSection("データ管理＆バックアップ", Icons.Default.Storage) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(SettingsInset).padding(3.dp)) {
                        Segment("▧  インポート (取込)", dataTab == "import", Modifier.weight(1f)) { dataTab = "import" }
                        Segment("⇩  エクスポート (保存)", dataTab == "export", Modifier.weight(1f)) { dataTab = "export" }
                    }
                    if (dataTab == "import") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Label("対応証券口座フォーマット", 9, SettingsMuted)
                            Label("◉ 自動フォーマット判定", 8, SettingsMint)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("SBI証券", "楽天証券", "マネックス", "松井証券").forEach { broker ->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(SettingsInset).border(1.dp, SettingsBorder, RoundedCornerShape(6.dp)).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Label(broker, 8, SettingsMint, true)
                                    Label("CSV読込", 7, SettingsMuted)
                                }
                            }
                        }
                        Column(
                            Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(9.dp))
                                .background(SettingsInset).border(1.dp, SettingsMint.copy(alpha = .48f), RoundedCornerShape(9.dp))
                                .clickable(onClick = onNavigateToCsv),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CloudUpload, null, tint = SettingsMint, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.height(3.dp))
                            Label("CSVファイルを選択またはドロップ", 9, OnSurface, true)
                            Label(".csv形式 (UTF-8・Shift-JIS自動判定 / 最大10MB)", 7, SettingsMuted)
                            Spacer(Modifier.height(4.dp))
                            Label("▱  ウィザードへ", 8, SettingsMint, true)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Label("◉ 前回インポート:          2024/10/24 18:30", 7, SettingsMuted)
                            Label("SBI証券 48銘柄", 7, SettingsMint)
                        }
                        Label("ⓘ  各証券会社CSVのダウンロード手順   保存データ上書き/追加", 7, SettingsMuted)
                    } else {
                        Label("登録中の保有株・配当情報・取得単価データを保存します。", 9, SettingsMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("▦  CSV形式で保存", Modifier.weight(1f)) { Toast.makeText(context, "全保有株CSVを書き出しました", Toast.LENGTH_SHORT).show() }
                            ActionButton("{ }  JSONバックアップ", Modifier.weight(1f)) { Toast.makeText(context, "JSONバックアップを作成しました", Toast.LENGTH_SHORT).show() }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Label("♢  CSVファイルは端末内でのみ安全に処理され、外部サーバーにアップロードされることはありません。", 7, SettingsMuted)
                }
            }
        }

        item {
            SettingsSection("外観・テーマ設定", Icons.Default.Palette) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label("カラーテーマ", 9, OnSurface, true)
                        Label(if (themeMode == "dark") "ダーク適用中" else if (themeMode == "light") "ライト" else "システム連動", 8, SettingsMint)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("dark" to "☾  ダーク", "light" to "☼  ライト", "system" to "▣  システム").forEach { (mode, name) ->
                            Segment(name, themeMode == mode, Modifier.weight(1f)) { viewModel.setThemeMode(mode) }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label("アクセントカラー", 9, OnSurface, true)
                        Label(selectedAccent.replaceFirstChar { it.uppercase() }, 8, SettingsMint)
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        accents.forEach { (name, color) ->
                            Box(Modifier.size(27.dp).clip(CircleShape).background(color).then(if (selectedAccent == name) Modifier.border(2.dp, OnSurface, CircleShape) else Modifier).clickable { viewModel.setSelectedAccent(name) }, contentAlignment = Alignment.Center) {
                                if (selectedAccent == name) Icon(Icons.Default.Check, null, tint = OnSurface, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle("有機ELダーク (完全ブラック)", "ピクセル発光を最小化し黒色消費電力を抑制", isOledBlack, { viewModel.setIsOledBlack(it) }, tag = "節電")
                }
            }
        }

        item {
            SettingsSection("表示・計算カスタマイズ", Icons.Default.Tune) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label("配当利回りの計算基準", 9, OnSurface, true); Label("利回り計算ロジック", 7, SettingsMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BasisChoice("評価額基準", "現在値ベース", calcBasis == "eval", Modifier.weight(1f)) { calcBasis = "eval" }
                        BasisChoice("取得額基準", "買付単価 (YOC)", calcBasis == "cost", Modifier.weight(1f)) { calcBasis = "cost" }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle("税金計算設定", "税引後表示 (国内税 20.315% を控除)", taxDeduction, { taxDeduction = it })
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle("米国株 現地源泉徴収控除 (10%)", "外国税額控除前の手取り概算に反映", usTaxDeduction, { usTaxDeduction = it }, tag = "新NISA連動")
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("目標利回り (ロードマップ試算)", 9, OnSurface, true); Label("シミュレーション基準   $targetYield%", 8, SettingsMint) }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("3.5" to "3.5% (堅実)", "4.0" to "4.0% (標準)", "4.5" to "4.5% (積極)").forEach { (value, label) ->
                            Segment(label, targetYield == value, Modifier.weight(1f)) { targetYield = value }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("構成比「その他」集約閾値", 9, OnSurface, true); Label("$threshold%", 8, SettingsMint) }
                    ThresholdSlider(threshold) { threshold = it }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("3% (詳細)", 7, SettingsMuted); Label("5% (推奨)", 7, SettingsMint); Label("10% (簡易)", 7, SettingsMuted) }
                }
            }
        }

        item {
            SettingsSection("通知・リマインダー設定", Icons.Default.Notifications) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingToggle("配当金の権利落ち日・入金通知", "権利付最終日および振込予定日をプッシュ通知", notifyExDiv, { notifyExDiv = it }, tag = "NEW")
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle("買場シグナル・株価アラート", "利回り4.5%突破時や年初来安値更新時", notifyBuySignal, { notifyBuySignal = it })
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Label("通知時間帯の指定", 9, OnSurface, true); Label("市場寄り前・引け後まとめ", 7, SettingsMuted) }
                        Label("朝 08:30 / 夕 17:30", 8, SettingsMint)
                    }
                }
            }
        }

        item {
            SettingsSection("セキュリティ＆プライバシー", Icons.Default.Shield) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingToggle("生体認証 (Face ID / 指紋認証)", "アプリ起動時のロック解除", biometrics, { biometrics = it }, tag = "推奨")
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle("起動時の金額伏字マスク", "電車内など外出時の画面閲覧用", maskAtStartup, { maskAtStartup = it }, tag = "覗き見防止")
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Label("ローカルデータ完全消去", 9, ErrorRed, true); Label("端末内の保有・設定データをすべて削除", 7, SettingsMuted) }
                        Box(Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFF421D2A)).border(1.dp, Color(0xFF8D3341), RoundedCornerShape(5.dp)).clickable { confirmReset = true }.padding(horizontal = 9.dp, vertical = 5.dp)) { Label("初期化", 8, ErrorRed, true) }
                    }
                }
            }
        }

        item {
            SettingsSection("アプリ情報・サポート", Icons.Default.Info) {
                Column {
                    SupportRow("お問い合わせ・フィードバック", onNavigateToFeedback)
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SupportRow("利用規約＆プライバシーポリシー", onNavigateToTerms)
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SupportRow("アプリをレビューで応援", {})
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) { Label("バージョン", 8, SettingsMuted); Label("v1.2.0 (Build 248)", 8, OnSurface, true) }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Label("♙  金融データは端末内暗号化保存されています", 7, SettingsMint)
                Spacer(Modifier.height(3.dp)); Label("© 2025 Diview", 6, SettingsMuted)
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("データを初期化", color = OnSurface) },
        text = { Text("端末内の保有・設定データを初期化しますか？", color = SettingsMuted) },
        confirmButton = { TextButton(onClick = { confirmReset = false; Toast.makeText(context, "データを初期化しました", Toast.LENGTH_SHORT).show() }) { Text("初期化", color = ErrorRed) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("キャンセル", color = SettingsMuted) } },
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
    Box(modifier.clip(RoundedCornerShape(6.dp)).then(if (selected) Modifier.background(SettingsMint.copy(alpha = .16f)).border(.8.dp, SettingsMint, RoundedCornerShape(6.dp)) else Modifier).clickable(onClick = onClick).padding(horizontal = 3.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) SettingsMint else SettingsMuted, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, onChanged: (Boolean) -> Unit, tag: String? = null) {
    Row(Modifier.fillMaxWidth().clickable { onChanged(!checked) }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
    Column(modifier.clip(RoundedCornerShape(7.dp)).background(if (selected) SettingsMint.copy(alpha = 0.14f) else SettingsInset).border(1.dp, if (selected) SettingsMint else SettingsBorder, RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label(title, 8, if (selected) OnSurface else SettingsMuted, true)
            Icon(if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null, tint = if (selected) SettingsMint else SettingsMuted, modifier = Modifier.size(12.dp))
        }
        Label(subtitle, 7, SettingsMuted)
    }
}

@Composable
private fun ActionButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(34.dp).clip(RoundedCornerShape(7.dp)).background(SettingsInset).border(1.dp, SettingsBorder, RoundedCornerShape(7.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Label(label, 8, OnSurface, true) }
}

@Composable
private fun SupportRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Label(label, 8, OnSurface, true)
        Icon(Icons.Default.ChevronRight, null, tint = SettingsMuted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun ThresholdSlider(value: Int, onValueChange: (Int) -> Unit) {
    val trackColor = SurfaceContainerHighest
    val accentColor = PrimaryEmeraldContainer
    Canvas(Modifier.fillMaxWidth().height(22.dp).pointerInput(Unit) {
        detectTapGestures { onValueChange((3 + (it.x / size.width * 7).roundToInt()).coerceIn(3, 10)) }
    }.pointerInput(Unit) {
        detectDragGestures { change, _ -> change.consume(); onValueChange((3 + (change.position.x / size.width * 7).roundToInt()).coerceIn(3, 10)) }
    }) {
        val y = size.height / 2f
        val x = (value - 3) / 7f * size.width
        drawLine(trackColor, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(accentColor, Offset(0f, y), Offset(x, y), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(accentColor, 6.dp.toPx(), Offset(x, y))
    }
}
