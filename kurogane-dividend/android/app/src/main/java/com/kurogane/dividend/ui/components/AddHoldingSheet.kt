package com.kurogane.dividend.ui.components

import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.kurogane.dividend.ui.networkErrorMessage
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.data.CloudflareSecurity
import com.kurogane.dividend.data.runCatchingCancellable
import com.kurogane.dividend.model.HoldingAllocation
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.PortfolioMath
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
    usdJpyRate: Double? = null,
    usWithholdingEnabled: Boolean = true,
    existingHoldings: List<Holding> = emptyList(),
    onResolveSecurity: suspend (code: String) -> CloudflareSecurity?,
    onRegisterCollective: suspend (code: String, allocations: List<HoldingAllocation>, replaceExisting: Boolean) -> Boolean
) {
    if (!visible) return

    val presets = remember {
        listOf(
            PresetStock("7203", "トヨタ自動車", "東証P • 輸送用機器", 2600.0, 2.80, 72.8, "¥"),
            PresetStock("8058", "三菱商事", "東証P • 卸売業", 3240.0, 3.52, 100.0, "¥"),
            PresetStock("8316", "三井住友FG", "東証P • 銀行業", 3150.0, 4.13, 130.0, "¥"),
            PresetStock("9432", "日本電信電話", "東証P • 情報・通信業", 154.2, 3.37, 5.2, "¥"),
            PresetStock("AAPL", "アップル", "NASDAQ • テクノロジー", 220.0, 0.52, 1.0, "$")
        )
    }

    var stockCodeInput by rememberSaveable { mutableStateOf(prefilledCode.ifEmpty { "8316" }) }
    var selectedStock by remember {
        mutableStateOf(
            presets.find { it.code == stockCodeInput } ?: presets[2]
        )
    }
    var isRegistering by remember { mutableStateOf(false) }
    var registerError by remember { mutableStateOf(false) }
    var registerErrorDetail by remember { mutableStateOf<String?>(null) }
    val resources = LocalContext.current.resources
    val resolveSecurity by rememberUpdatedState(onResolveSecurity)
    var resolvedSecurity by remember { mutableStateOf<CloudflareSecurity?>(null) }
    val registrationScope = rememberCoroutineScope()

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
    val existingHolding = existingHoldings.firstOrNull { it.code.equals(stockCodeInput.trim(), ignoreCase = true) }

    LaunchedEffect(prefilledCode, existingHolding?.id) {
        val holding = existingHoldings.firstOrNull { it.code.equals(prefilledCode.trim(), ignoreCase = true) }
            ?: existingHolding
            ?: return@LaunchedEffect
        val sharesByAccount = when {
            holding.accountType.contains("つみたて") || holding.accountType.contains("積立") -> listOf(0, holding.shares, 0, 0)
            holding.accountType.contains("特定") -> listOf(0, 0, holding.shares, 0)
            holding.accountType.contains("一般") -> listOf(0, 0, 0, holding.shares)
            holding.accountType.contains("複数") -> {
                val taxFree = holding.effectiveTaxFreeShares
                listOf(taxFree, 0, (holding.shares - taxFree).coerceAtLeast(0), 0)
            }
            PortfolioMath.isTaxFree(holding.accountType) -> listOf(holding.shares, 0, 0, 0)
            else -> listOf(0, 0, holding.shares, 0)
        }
        nisaShares = sharesByAccount[0].toString()
        tsumitateShares = sharesByAccount[1].toString()
        tokuteiShares = sharesByAccount[2].toString()
        generalShares = sharesByAccount[3].toString()
        val acquisitionPrice = holding.acqPrice.toString()
        nisaPrice = acquisitionPrice
        tsumitatePrice = acquisitionPrice
        tokuteiPrice = acquisitionPrice
        generalPrice = acquisitionPrice
    }

    // Dynamic update prices when stock selection changes
    fun updateSelectedStock(stock: PresetStock) {
        selectedStock = stock
        stockCodeInput = stock.code
        val acquisitionPrice = existingHoldings.firstOrNull { it.code.equals(stock.code, ignoreCase = true) }?.acqPrice
            ?: stock.price
        nisaPrice = acquisitionPrice.toString()
        tsumitatePrice = acquisitionPrice.toString()
        tokuteiPrice = acquisitionPrice.toString()
        generalPrice = acquisitionPrice.toString()
    }

    // Presets or custom stock changes
    LaunchedEffect(stockCodeInput) {
        val normalizedCode = stockCodeInput.trim().uppercase()
        val matched = presets.find { it.code.trim().uppercase() == normalizedCode }
        if (matched != null && matched != selectedStock) {
            resolvedSecurity = null
            updateSelectedStock(matched)
        } else if (matched != null) {
            resolvedSecurity = null
        } else if (resolvedSecurity?.code?.equals(normalizedCode, ignoreCase = true) == true) {
            // Already resolved this exact code from the remote master.
        } else if (normalizedCode.matches(Regex("[A-Z0-9]{4}"))) {
            kotlinx.coroutines.delay(350)
            val security = runCatchingCancellable { resolveSecurity(normalizedCode) }.getOrNull()
            if (normalizedCode == stockCodeInput.trim().uppercase() && security != null) {
                resolvedSecurity = security
                val knownPreset = presets.firstOrNull { it.code.equals(normalizedCode, ignoreCase = true) }
                val price = security.marketPrice ?: knownPreset?.price ?: 0.0
                val dps = security.annualDividendPerShare ?: knownPreset?.dps ?: 0.0
                updateSelectedStock(
                    PresetStock(
                        code = security.code,
                        name = security.name,
                        sector = security.industry?.let { "東証P • $it" } ?: "東証銘柄",
                        price = price,
                        yieldRate = if (price > 0.0) dps / price * 100 else 0.0,
                        dps = dps,
                        currency = "¥"
                    )
                )
            }
        }
    }

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)
    val isUSD = selectedStock.currency == "$"
    val fxRate = if (isUSD) usdJpyRate ?: 0.0 else 1.0

    // Calculations
    val nShares = nisaShares.toIntOrNull() ?: 0
    val nPrice = nisaPrice.toDoubleOrNull()?.takeIf(PortfolioMath::validMoney) ?: 0.0
    val nisaDividend = com.kurogane.dividend.model.DividendTax.net(nShares * selectedStock.dps * fxRate, 1.0, isUSD && usWithholdingEnabled)
    val nisaCost = nShares * nPrice * fxRate

    val tShares = tsumitateShares.toIntOrNull() ?: 0
    val tPrice = tsumitatePrice.toDoubleOrNull()?.takeIf(PortfolioMath::validMoney) ?: 0.0
    val tsumitateDividend = com.kurogane.dividend.model.DividendTax.net(tShares * selectedStock.dps * fxRate, 1.0, isUSD && usWithholdingEnabled)
    val tsumitateCost = tShares * tPrice * fxRate

    val tkShares = tokuteiShares.toIntOrNull() ?: 0
    val tkPrice = tokuteiPrice.toDoubleOrNull()?.takeIf(PortfolioMath::validMoney) ?: 0.0
    val tokuteiGrossDiv = tkShares * selectedStock.dps * fxRate
    val tokuteiDividend = com.kurogane.dividend.model.DividendTax.net(tokuteiGrossDiv, 0.0, isUSD && usWithholdingEnabled)
    val tokuteiCost = tkShares * tkPrice * fxRate

    val gShares = generalShares.toIntOrNull() ?: 0
    val gPrice = generalPrice.toDoubleOrNull()?.takeIf(PortfolioMath::validMoney) ?: 0.0
    val generalGrossDiv = gShares * selectedStock.dps * fxRate
    val generalDividend = com.kurogane.dividend.model.DividendTax.net(generalGrossDiv, 0.0, isUSD && usWithholdingEnabled)
    val generalCost = gShares * gPrice * fxRate

    // Summary Box metrics
    val totalSharesLong = listOf(nShares, tShares, tkShares, gShares).sumOf { it.toLong() }
    val totalShares = totalSharesLong.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val inputValid = totalSharesLong in 1..Int.MAX_VALUE.toLong() &&
        listOf(nisaShares, tsumitateShares, tokuteiShares, generalShares).all { (it.toIntOrNull() ?: -1) >= 0 } &&
        listOf(nShares to nisaPrice, tShares to tsumitatePrice, tkShares to tokuteiPrice, gShares to generalPrice)
            .all { (quantity, value) -> quantity == 0 || value.toDoubleOrNull()?.let(PortfolioMath::validMoney) == true }
    val totalCost = nisaCost + tsumitateCost + tokuteiCost + generalCost
    val weightedAvgAcqPrice = if (totalShares > 0) (nShares * nPrice + tShares * tPrice + tkShares * tkPrice + gShares * gPrice) / totalShares else selectedStock.price
    val totalNetDividend = nisaDividend + tsumitateDividend + tokuteiDividend + generalDividend
    val nisaTaxBenefits = (nShares + tShares) * selectedStock.dps * 0.20315 * fxRate

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { targetValue -> targetValue != SheetValue.Hidden }
        ),
        containerColor = SurfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = SurfaceContainerHighest)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            AdaptiveRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryEmeraldContainer.copy(alpha = 0.15f))
                            .border(1.dp, PrimaryEmeraldContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        AdaptiveRow(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.add_holding_text),
                                color = OnSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = AppTextSize.body
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PrimaryEmeraldContainer.copy(alpha = 0.2f))
                                    .border(0.5.dp, PrimaryEmeraldContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    stringResource(R.string.add_holding_text_2),
                                    color = PrimaryEmerald,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.add_holding_text_3),
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense
                        )
                    }
                }

                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

            // 1. Stock Code Input Box
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Tag, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.add_holding_text_4), color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        stringResource(R.string.add_holding_text_5 , selectedStock.name, String.format(java.util.Locale.JAPAN, "%.2f", selectedStock.yieldRate)),
                        color = PrimaryEmerald,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = stockCodeInput,
                    onValueChange = { stockCodeInput = it },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = resources.getString(R.string.add_holding_text_4) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark,
                        focusedBorderColor = PrimaryEmerald,
                        unfocusedBorderColor = SurfaceContainerHighest,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                if (registerError) {
                    registerErrorDetail?.let { StatusMessage(it, color = ErrorRed) }
                    Text(
                        stringResource(R.string.add_holding_text_6),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = AppTextSize.dense
                    )
                }

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
                                .background(if (active) PrimaryEmeraldContainer.copy(alpha = 0.15f) else SurfaceContainer)
                                .border(BorderStroke(1.dp, if (active) PrimaryEmerald else Color.Transparent), RoundedCornerShape(8.dp))
                                .minimumInteractiveComponentSize()
                                .clickable { updateSelectedStock(stock) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                stringResource(R.string.add_holding_1_s_2_s , stock.code, stock.name),
                                color = if (active) PrimaryEmerald else OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Ticker detail preview tag
                AdaptiveRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainer)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = 54.dp).heightIn(min = 24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BackgroundDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            selectedStock.code,
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = AppTextSize.dense,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedStock.name, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                        Text(selectedStock.sector, color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.add_holding_1_s_2_s_2 , selectedStock.currency, currencyFormat.format(selectedStock.price.toLong())),
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = AppTextSize.caption,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(stringResource(R.string.add_holding_text_7 , String.format(java.util.Locale.JAPAN, "%.2f", selectedStock.yieldRate)), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. Portfolio Allocation Matrix by Account
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.CorporateFare, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.add_holding_text_8), color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(PrimaryEmeraldContainer.copy(alpha = 0.15f))
                            .heightIn(min = 48.dp)
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
                            Text(stringResource(R.string.add_holding_text_9), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceContainerHighest)
                            .heightIn(min = 48.dp)
                            .clickable {
                                nisaShares = "0"
                                tsumitateShares = "0"
                                tokuteiShares = "0"
                                generalShares = "0"
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = OnSurface, modifier = Modifier.size(10.dp))
                            Text(stringResource(R.string.add_holding_text_10), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Account Cards list
                val accounts = listOf(
                        Quadruple("新NISA 成長投資枠", resources.getString(R.string.roadmap_label_8), nisaDividend, nisaShares, nisaPrice, { value: String -> nisaShares = value }, { value: String -> nisaPrice = value }),
                        Quadruple("新NISA つみたて枠", resources.getString(R.string.roadmap_label_8), tsumitateDividend, tsumitateShares, tsumitatePrice, { value: String -> tsumitateShares = value }, { value: String -> tsumitatePrice = value }),
                        Quadruple("特定口座 (源泉徴収あり)", resources.getString(R.string.add_holding_sheet_label), tokuteiDividend, tokuteiShares, tokuteiPrice, { value: String -> tokuteiShares = value }, { value: String -> tokuteiPrice = value }),
                        Quadruple("一般口座", resources.getString(R.string.add_holding_sheet_label), generalDividend, generalShares, generalPrice, { value: String -> generalShares = value }, { value: String -> generalPrice = value })
                )

                existingHolding?.let { holding ->
                    Text(
                        stringResource(R.string.add_holding_text_25, currencyFormat.format(holding.shares)),
                        color = PrimaryEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = AppTextSize.caption
                    )
                    if (holding.accountType.contains("複数")) {
                        Text(
                            stringResource(R.string.add_holding_text_26),
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense
                        )
                    }
                }

                accounts.forEach { (title, tag, divAmount, sharesVal, priceVal, onSharesChange, onPriceChange) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdaptiveRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AdaptiveRow(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (title.contains("NISA")) PrimaryEmerald else Color(0xFF818CF8))
                                    )
                                    Text(title, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(SurfaceContainerHighest)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(tag, color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    stringResource(R.string.add_holding_text_11 , currencyFormat.format(divAmount.toLong())),
                                        color = if (divAmount > 0) PrimaryEmerald else OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.caption
                                )
                            }

                            AdaptiveRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Shares Input Field with Helper tags
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.add_holding_text_12), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(SurfaceContainerHighest)
                                                .heightIn(min = 36.dp)
                                                .clickable {
                                                    val curr = sharesVal.toIntOrNull() ?: 0
                                                    onSharesChange((curr - 100).coerceAtLeast(0).toString())
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(stringResource(R.string.add_holding_text_13), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(SurfaceContainerHighest)
                                                .heightIn(min = 36.dp)
                                                .clickable {
                                                    val curr = sharesVal.toIntOrNull() ?: 0
                                                    onSharesChange((curr + 100).toString())
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(stringResource(R.string.add_holding_text_14), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    OutlinedTextField(
                                        value = sharesVal,
                                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = resources.getString(R.string.account_input_label, title, resources.getString(R.string.add_holding_text_12)) },
                                        onValueChange = onSharesChange,
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = BackgroundDark,
                                            unfocusedContainerColor = BackgroundDark,
                                            focusedBorderColor = PrimaryEmerald,
                                            unfocusedBorderColor = SurfaceContainerHighest,
                                            focusedTextColor = OnSurface,
                                            unfocusedTextColor = OnSurface
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                // Price Input Field
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.add_holding_text_15), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    OutlinedTextField(
                                        value = priceVal,
                                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = resources.getString(R.string.account_input_label, title, resources.getString(R.string.add_holding_text_15)) },
                                        onValueChange = onPriceChange,
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = BackgroundDark,
                                            unfocusedContainerColor = BackgroundDark,
                                            focusedBorderColor = PrimaryEmerald,
                                            unfocusedBorderColor = SurfaceContainerHighest,
                                            focusedTextColor = OnSurface,
                                            unfocusedTextColor = OnSurface
                                        ),
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
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.add_holding_text_16), color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PrimaryEmeraldContainer.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.add_holding_text_17), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }

                // Summary Grid Row (2x2 boxes)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Box 1
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(stringResource(R.string.add_holding_text_18), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(stringResource(R.string.add_holding_text_19 , totalShares), color = OnSurface, fontWeight = FontWeight.Black, fontSize = AppTextSize.body)
                            }
                        }
                        // Box 2
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(stringResource(R.string.add_holding_text_20), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(stringResource(R.string.add_holding_1_s , currencyFormat.format(weightedAvgAcqPrice.toLong())), color = OnSurface, fontWeight = FontWeight.Black, fontSize = AppTextSize.body)
                            }
                        }
                    }

                    AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Box 3
                        Card(
                            modifier = Modifier.weight(1f).border(BorderStroke(0.5.dp, PrimaryEmeraldContainer.copy(alpha = 0.3f)), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(stringResource(R.string.add_holding_text_21), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(stringResource(R.string.add_holding_text_22 , currencyFormat.format(totalNetDividend.toLong())), color = PrimaryEmerald, fontWeight = FontWeight.Black, fontSize = AppTextSize.body)
                            }
                        }
                        // Box 4
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(stringResource(R.string.add_holding_text_23), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(stringResource(R.string.add_holding_text_24 , currencyFormat.format(nisaTaxBenefits.toLong())), color = SkyBlue, fontWeight = FontWeight.Black, fontSize = AppTextSize.body)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Actions Row
            AdaptiveRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(0.4f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.cancel), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                }

                Button(
                    onClick = {
                        if (inputValid && !isRegistering) {
                            registerError = false
                            registerErrorDetail = null
                            isRegistering = true
                            registrationScope.launch {
                                try {
                                    val result = runCatchingCancellable {
                                        onRegisterCollective(stockCodeInput.trim().uppercase(), listOf(
                                            HoldingAllocation(nShares, nPrice, "新NISA成長枠"),
                                            HoldingAllocation(tShares, tPrice, "新NISAつみたて投資枠"),
                                            HoldingAllocation(tkShares, tkPrice, "特定口座"),
                                            HoldingAllocation(gShares, gPrice, "一般口座")
                                        ), existingHolding != null)
                                    }
                                    registerError = !result.getOrDefault(false)
                                    registerErrorDetail = result.exceptionOrNull()?.let { networkErrorMessage(resources, it) }
                                } finally { isRegistering = false }
                            }
                        }
                    },
                    modifier = Modifier.weight(0.6f).heightIn(min = 48.dp),
                    enabled = inputValid && !isRegistering,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryEmeraldContainer,
                        contentColor = OnPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isRegistering) {
                            CircularProgressIndicator(color = OnPrimaryContainer, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, tint = OnPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                        Text(if (isRegistering) resources.getString(R.string.add_holding_label) else resources.getString(R.string.add_holding_label_2), color = OnPrimaryContainer, fontWeight = FontWeight.Black, fontSize = AppTextSize.dense)
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
