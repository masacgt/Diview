package com.kurogane.dividend.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

data class PresetStock(
    val code: String,
    val name: String,
    val sector: String,
    val price: Double,
    val yieldRate: Double,
    val dps: Double,
    val currency: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHoldingSheet(
    visible: Boolean,
    prefilledCode: String,
    onDismiss: () -> Unit,
    onRegisterCollective: (code: String, shares: Int, acqPrice: Double, accountType: String) -> Unit
) {
    if (!visible) return

    val presets = remember {
        listOf(
            PresetStock("7203", "トヨタ自動車", "東証P • 輸送用機器", 2600.0, 2.80, 72.8, "¥"),
            PresetStock("8058", "三菱商事", "東証P • 卸売業", 3240.0, 3.52, 100.0, "¥"),
            PresetStock("8316", "三井住友FG", "東証P • 銀行業", 3150.0, 4.13, 130.0, "¥"),
            PresetStock("9432", "日本電信電話", "東証P • 情報通信", 154.2, 3.37, 5.2, "¥"),
            PresetStock("AAPL", "Apple Inc.", "NASDAQ • テクノロジー", 220.0, 0.52, 1.0, "$")
        )
    }

    var stockCodeInput by remember { mutableStateOf(prefilledCode.ifEmpty { "8316" }) }
    var selectedStock by remember {
        mutableStateOf(
            presets.find { it.code == stockCodeInput } ?: presets[2]
        )
    }

    // Dynamic prefill when prefilledCode changes
    LaunchedEffect(prefilledCode) {
        if (prefilledCode.isNotEmpty()) {
            stockCodeInput = prefilledCode
            presets.find { it.code == prefilledCode }?.let {
                selectedStock = it
            }
        }
    }

    // Accounts Matrix States
    var nisaShares by remember { mutableStateOf("100") }
    var nisaPrice by remember { mutableStateOf(selectedStock.price.toString()) }

    var tsumitateShares by remember { mutableStateOf("0") }
    var tsumitatePrice by remember { mutableStateOf(selectedStock.price.toString()) }

    var tokuteiShares by remember { mutableStateOf("0") }
    var tokuteiPrice by remember { mutableStateOf(selectedStock.price.toString()) }

    var generalShares by remember { mutableStateOf("0") }
    var generalPrice by remember { mutableStateOf(selectedStock.price.toString()) }

    // Dynamic update prices when stock selection changes
    fun updateSelectedStock(stock: PresetStock) {
        selectedStock = stock
        stockCodeInput = stock.code
        nisaPrice = stock.price.toString()
        tsumitatePrice = stock.price.toString()
        tokuteiPrice = stock.price.toString()
        generalPrice = stock.price.toString()
    }

    // Presets or custom stock changes
    LaunchedEffect(stockCodeInput) {
        val matched = presets.find { it.code.trim().uppercase() == stockCodeInput.trim().uppercase() }
        if (matched != null && matched != selectedStock) {
            updateSelectedStock(matched)
        }
    }

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)
    val isUSD = selectedStock.currency == "$"
    val fxRate = if (isUSD) 154.0 else 1.0

    // Calculations
    val nShares = nisaShares.toIntOrNull() ?: 0
    val nPrice = nisaPrice.toDoubleOrNull() ?: selectedStock.price
    val nisaDividend = nShares * selectedStock.dps * fxRate
    val nisaCost = nShares * nPrice * fxRate

    val tShares = tsumitateShares.toIntOrNull() ?: 0
    val tPrice = tsumitatePrice.toDoubleOrNull() ?: selectedStock.price
    val tsumitateDividend = tShares * selectedStock.dps * fxRate
    val tsumitateCost = tShares * tPrice * fxRate

    val tkShares = tokuteiShares.toIntOrNull() ?: 0
    val tkPrice = tokuteiPrice.toDoubleOrNull() ?: selectedStock.price
    val tokuteiGrossDiv = tkShares * selectedStock.dps * fxRate
    val tokuteiDividend = tokuteiGrossDiv * 0.79685
    val tokuteiCost = tkShares * tkPrice * fxRate

    val gShares = generalShares.toIntOrNull() ?: 0
    val gPrice = generalPrice.toDoubleOrNull() ?: selectedStock.price
    val generalGrossDiv = gShares * selectedStock.dps * fxRate
    val generalDividend = generalGrossDiv * 0.79685
    val generalCost = gShares * gPrice * fxRate

    // Summary Box metrics
    val totalShares = nShares + tShares + tkShares + gShares
    val totalCost = nisaCost + tsumitateCost + tokuteiCost + generalCost
    val weightedAvgAcqPrice = if (totalShares > 0) totalCost / totalShares / fxRate else selectedStock.price
    val totalNetDividend = nisaDividend + tsumitateDividend + tokuteiDividend + generalDividend
    val nisaTaxBenefits = (nShares + tShares) * selectedStock.dps * 0.20315 * fxRate

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                onClick = onDismiss,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(SurfaceContainerLow)
                .border(BorderStroke(1.dp, Color(0xFF1E293B)), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(
                    onClick = {},
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Grab Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(48.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF334155))
                )

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "複数口座一括配分登録",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                        .border(0.5.dp, Color(0xFF10B981), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "マトリックス同時入力",
                                        color = PrimaryEmerald,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                "口座間の株数・取得単価を1画面でまとめて設定",
                                color = OnSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                // 1. Stock Code Input Box
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Tag, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                            Text("銘柄コード / 企業名", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${selectedStock.name} (利回り ${selectedStock.yieldRate}%)",
                            color = PrimaryEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = stockCodeInput,
                        onValueChange = { stockCodeInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BackgroundDark,
                            unfocusedContainerColor = BackgroundDark,
                            focusedBorderColor = PrimaryEmerald,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Presets selection row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { stock ->
                            val active = selectedStock.code == stock.code
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) Color(0xFF10B981).copy(alpha = 0.15f) else SurfaceContainer)
                                    .border(BorderStroke(1.dp, if (active) PrimaryEmerald else Color.Transparent), RoundedCornerShape(8.dp))
                                    .clickable { updateSelectedStock(stock) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "${stock.code} ${stock.name}",
                                    color = if (active) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Ticker detail preview tag
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainer)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 54.dp, height = 24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(BackgroundDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                selectedStock.code,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedStock.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(selectedStock.sector, color = OnSurfaceVariant, fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${selectedStock.currency}${currencyFormat.format(selectedStock.price.toLong())}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("配当利回り ${selectedStock.yieldRate}%", color = PrimaryEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 2. Portfolio Allocation Matrix by Account
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CorporateFare, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                            Text("口座別 ポートフォリオ配分マトリックス", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    .clickable {
                                        nisaPrice = selectedStock.price.toString()
                                        tsumitatePrice = selectedStock.price.toString()
                                        tokuteiPrice = selectedStock.price.toString()
                                        generalPrice = selectedStock.price.toString()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(10.dp))
                                    Text("現在値を適用", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF334155))
                                    .clickable {
                                        nisaShares = "0"
                                        tsumitateShares = "0"
                                        tokuteiShares = "0"
                                        generalShares = "0"
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                    Text("株数クリア", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Account Cards list
                    val accounts = listOf(
                        Quadruple("新NISA 成長投資枠", "非課税 0%", nisaDividend, nisaShares, nisaPrice, { value: String -> nisaShares = value }, { value: String -> nisaPrice = value }),
                        Quadruple("新NISA つみたて枠", "非課税 0%", tsumitateDividend, tsumitateShares, tsumitatePrice, { value: String -> tsumitateShares = value }, { value: String -> tsumitatePrice = value }),
                        Quadruple("特定口座 (源泉徴収あり)", "課税 20.315%", tokuteiDividend, tokuteiShares, tokuteiPrice, { value: String -> tokuteiShares = value }, { value: String -> tokuteiPrice = value }),
                        Quadruple("一般口座", "課税 20.315%", generalDividend, generalShares, generalPrice, { value: String -> generalShares = value }, { value: String -> generalPrice = value })
                    )

                    accounts.forEach { (title, tag, divAmount, sharesVal, priceVal, onSharesChange, onPriceChange) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (title.contains("NISA")) PrimaryEmerald else Color(0xFF818CF8))
                                        )
                                        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF334155))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(tag, color = OnSurfaceVariant, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        "¥${currencyFormat.format(divAmount.toLong())}/年",
                                        color = if (divAmount > 0) PrimaryEmerald else OnSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Shares Input Field with Helper tags
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("株数 / 口数", color = OnSurfaceVariant, fontSize = 10.sp)
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF334155))
                                                        .clickable {
                                                            val curr = sharesVal.toIntOrNull() ?: 0
                                                            onSharesChange((curr + 10).toString())
                                                        }
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text("+10", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF334155))
                                                        .clickable {
                                                            val curr = sharesVal.toIntOrNull() ?: 0
                                                            onSharesChange((curr + 100).toString())
                                                        }
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text("+100", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = sharesVal,
                                            onValueChange = onSharesChange,
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = BackgroundDark,
                                                unfocusedContainerColor = BackgroundDark,
                                                focusedBorderColor = PrimaryEmerald,
                                                unfocusedBorderColor = Color(0xFF334155),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            modifier = Modifier.fillMaxWidth().height(42.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }

                                    // Price Input Field
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("取得単価", color = OnSurfaceVariant, fontSize = 10.sp)
                                        OutlinedTextField(
                                            value = priceVal,
                                            onValueChange = onPriceChange,
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = BackgroundDark,
                                                unfocusedContainerColor = BackgroundDark,
                                                focusedBorderColor = PrimaryEmerald,
                                                unfocusedBorderColor = Color(0xFF334155),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            modifier = Modifier.fillMaxWidth().height(42.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. All Accounts Total Allocation Summary Box
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Analytics, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                            Text("全口座 総合アロケーションサマリー", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("手取り最大化", color = PrimaryEmerald, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Summary Grid Row (2x2 boxes)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Box 1
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("合計保有株数", color = OnSurfaceVariant, fontSize = 9.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("${totalShares} 株", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }
                            // Box 2
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("加重平均取得単価", color = OnSurfaceVariant, fontSize = 9.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("¥${currencyFormat.format(weightedAvgAcqPrice.toLong())}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Box 3
                            Card(
                                modifier = Modifier.weight(1f).border(BorderStroke(0.5.dp, Color(0xFF10B981).copy(alpha = 0.3f)), RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("年間配当 (手取り総額)", color = OnSurfaceVariant, fontSize = 9.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("¥${currencyFormat.format(totalNetDividend.toLong())} /年", color = PrimaryEmerald, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }
                            // Box 4
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("NISA節税メリット", color = OnSurfaceVariant, fontSize = 9.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("+¥${currencyFormat.format(nisaTaxBenefits.toLong())} /年", color = Color(0xFF38BDF8), fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Footer Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.4f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("キャンセル", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (totalShares > 0) {
                                // Find account with shares to apply
                                val primaryAccount = when {
                                    nShares > 0 -> "新NISA成長枠"
                                    tShares > 0 -> "新NISAつみたて投資枠"
                                    tkShares > 0 -> "特定口座"
                                    else -> "一般口座"
                                }
                                onRegisterCollective(selectedStock.code, totalShares, weightedAvgAcqPrice, primaryAccount)
                            }
                        },
                        modifier = Modifier.weight(0.6f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmeraldContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(16.dp))
                            Text("すべての口座にまとめて登録する", color = OnPrimary, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// Quadruple helper data container for accounts
data class Quadruple<A, B, C, D, E, F, G>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G
)
