package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kurogane.dividend.data.CloudflareSecurity
import com.kurogane.dividend.data.runCatchingCancellable
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.HoldingAllocation
import com.kurogane.dividend.model.PortfolioMath
import com.kurogane.dividend.model.StockDetailResolver
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoadmapAllocationSheet(viewModel: DividendViewModel, onDismiss: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val currencyFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val usTaxDeduction by viewModel.usTaxDeduction.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var isRegistering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTickerInput by rememberSaveable { mutableStateOf("") }
    var selectedHolding by remember { mutableStateOf<Holding?>(null) }
    var selectedSecurity by remember { mutableStateOf<CloudflareSecurity?>(null) }
    var sharesNisaGrowth by rememberSaveable { mutableStateOf("0") }
    var sharesNisaTsumitate by rememberSaveable { mutableStateOf("0") }
    var sharesTokutei by rememberSaveable { mutableStateOf("0") }
    var sharesIppan by rememberSaveable { mutableStateOf("0") }
    var priceNisaGrowth by rememberSaveable { mutableStateOf("") }
    var priceNisaTsumitate by rememberSaveable { mutableStateOf("") }
    var priceTokutei by rememberSaveable { mutableStateOf("") }
    var priceIppan by rememberSaveable { mutableStateOf("") }
    val activeTicker = selectedHolding?.code.orEmpty()
    val activeStockName = selectedHolding?.name ?: resources.getString(R.string.roadmap_security_unselected)
    val activeStockMarket = selectedHolding?.market.orEmpty()
    val activeStockCurrency = selectedHolding?.currency ?: "¥"
    val activeStockPrice = selectedHolding?.price ?: 0.0
    val activeStockYield = selectedHolding?.liveYield ?: 0.0
    val activeStockDPS = selectedHolding?.dps ?: 0.0

    fun applyStock(input: String) {
        if (isLoading || isRegistering) return
        val code = PortfolioMath.normalizeCode(input)
        selectedTickerInput = code
        selectedHolding = null
        selectedSecurity = null
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val result = runCatchingCancellable {
                    viewModel.awaitRestoration()
                    val local = StockDetailResolver.resolve(code, viewModel.holdings.value, viewModel.watchlist.value, null)
                    val security = if (local == null) viewModel.resolveCloudflareSecurity(code) else null
                    val holding = local ?: StockDetailResolver.resolve(code, emptyList(), emptyList(), security)
                    checkNotNull(holding) { resources.getString(R.string.stock_lookup_failed) }
                    selectedSecurity = security
                    selectedHolding = holding
                    val price = holding.price.takeIf { it > 0 }?.toString().orEmpty()
                    priceNisaGrowth = price
                    priceNisaTsumitate = price
                    priceTokutei = price
                    priceIppan = price
                }
                errorMessage = result.exceptionOrNull()?.let { com.kurogane.dividend.ui.networkErrorMessage(resources, it) }
            } finally { isLoading = false }
        }
    }
    run {
        ModalBottomSheet(
            onDismissRequest = { if (!isRegistering) onDismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !isRegistering }),
            containerColor = BackgroundDark,
            tonalElevation = 8.dp,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Modal Header
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GridView, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.add_holding_text),
                                color = OnSurface,
                                fontSize = AppTextSize.body,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.add_holding_text_3),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { if (!isRegistering) onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider(color = BorderColor)

                // Search Bar Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.roadmap_text_32),
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    AdaptiveRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = selectedTickerInput,
                            enabled = !isLoading && !isRegistering,
                            onValueChange = { selectedTickerInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceContainerHigh,
                                unfocusedContainerColor = SurfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            placeholder = { Text(stringResource(R.string.roadmap_text_33), fontSize = AppTextSize.dense, color = OnSurfaceVariant) },
                            singleLine = true
                        )
                        Button(
                            onClick = { applyStock(selectedTickerInput) },
                            enabled = !isLoading && !isRegistering && selectedTickerInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.heightIn(min = 46.dp)
                        ) {
                            Text(stringResource(R.string.roadmap_text_34), color = OnPrimary, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Candidate Chips matching Image 2
                    AdaptiveRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("AAPL", "7203", "8316", "9432").forEach { code ->
                            val label = when (code) {
                                "AAPL" -> "AAPL アップル"
                                "7203" -> "7203 トヨタ"
                                "8316" -> "8316 三井住友"
                                "9432" -> "9432 NTT"
                                else -> code
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (activeTicker == code) PrimaryEmerald.copy(alpha = 0.2f) else SurfaceContainerHigh,
                                border = BorderStroke(1.dp, if (activeTicker == code) PrimaryEmerald else Color.Transparent),
                                modifier = Modifier
                                    .clickable(enabled = !isLoading && !isRegistering) { applyStock(code) }
                            ) {
                                Text(
                                    text = label,
                                    color = if (activeTicker == code) PrimaryEmerald else OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                errorMessage?.let { com.kurogane.dividend.ui.components.StatusMessage(it) }
                // Preview uses the same resolved security that will be registered.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PrimaryEmerald.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        activeTicker.take(4),
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        activeStockName,
                                        color = OnSurface,
                                        fontSize = AppTextSize.body,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        activeStockMarket,
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0x1F10B981)
                                ) {
                                    Text(
                                        stringResource(if (selectedHolding == null) R.string.roadmap_security_unselected else R.string.roadmap_security_selected),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    stringResource(R.string.roadmap_text_36 , activeStockCurrency) + String.format(java.util.Locale.JAPAN, "%.2f", activeStockDPS),
                                    color = OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(10.dp))

                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.roadmap_text_37 , activeStockCurrency) + currencyFormat.format(activeStockPrice),
                                color = OnSurface,
                                fontSize = AppTextSize.caption,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                stringResource(R.string.roadmap_text_38) + String.format(java.util.Locale.JAPAN, "%.2f%%", activeStockYield),
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Header sub-label Matrix Allocation
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.add_holding_text_8),
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        stringResource(R.string.roadmap_text_39),
                        color = ErrorRed,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            sharesNisaGrowth = "0"
                            sharesNisaTsumitate = "0"
                            sharesTokutei = "0"
                            sharesIppan = "0"
                        }
                    )
                }

                // Account Allocation Cards Grid inside BottomSheet
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Account Row 1: 新NISA 成長投資枠
                    AccountMatrixCard(
                        title = resources.getString(R.string.roadmap_label_12),
                        isTaxFree = true,
                        shares = sharesNisaGrowth,
                        price = priceNisaGrowth,
                        currency = activeStockCurrency,
                        onSharesChange = { sharesNisaGrowth = it },
                        onPriceChange = { priceNisaGrowth = it },
                        usWithholdingEnabled = usTaxDeduction,
                        dps = activeStockDPS
                    )

                    // Account Row 2: 新NISA つみたて枠
                    AccountMatrixCard(
                        title = resources.getString(R.string.roadmap_label_13),
                        isTaxFree = true,
                        shares = sharesNisaTsumitate,
                        price = priceNisaTsumitate,
                        currency = activeStockCurrency,
                        onSharesChange = { sharesNisaTsumitate = it },
                        onPriceChange = { priceNisaTsumitate = it },
                        usWithholdingEnabled = usTaxDeduction,
                        dps = activeStockDPS
                    )

                    // Account Row 3: 特定口座 (源泉徴収あり)
                    AccountMatrixCard(
                        title = resources.getString(R.string.roadmap_label_14),
                        isTaxFree = false,
                        shares = sharesTokutei,
                        price = priceTokutei,
                        currency = activeStockCurrency,
                        onSharesChange = { sharesTokutei = it },
                        onPriceChange = { priceTokutei = it },
                        usWithholdingEnabled = usTaxDeduction,
                        dps = activeStockDPS
                    )

                    // Account Row 4: 一般口座 (確定申告)
                    AccountMatrixCard(
                        title = resources.getString(R.string.roadmap_label_15),
                        isTaxFree = false,
                        shares = sharesIppan,
                        price = priceIppan,
                        currency = activeStockCurrency,
                        onSharesChange = { sharesIppan = it },
                        onPriceChange = { priceIppan = it },
                        usWithholdingEnabled = usTaxDeduction,
                        dps = activeStockDPS
                    )
                }

                // Save Action Button inside BottomSheet
                Button(
                    onClick = {
                        if (isRegistering) return@Button
                        val entries = listOf(
                            Triple(sharesNisaGrowth, priceNisaGrowth, "新NISA成長枠"),
                            Triple(sharesNisaTsumitate, priceNisaTsumitate, "新NISAつみたて投資枠"),
                            Triple(sharesTokutei, priceTokutei, "特定口座"),
                            Triple(sharesIppan, priceIppan, "一般口座")
                        )
                        val allocations = entries.mapNotNull { (quantity, price, account) ->
                            val shares = quantity.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                            val cost = (if (shares == 0 && price.isBlank()) 0.0 else price.toDoubleOrNull())
                                ?.takeIf(PortfolioMath::validMoney) ?: return@mapNotNull null
                            HoldingAllocation(shares, cost, account)
                        }
                        if (allocations.size != entries.size || allocations.none { it.shares > 0 }) {
                            errorMessage = resources.getString(R.string.roadmap_allocation_invalid)
                            return@Button
                        }
                        isRegistering = true
                        errorMessage = null
                        val code = activeTicker
                        val security = selectedSecurity
                        scope.launch {
                            try {
                                val result = runCatchingCancellable { viewModel.registerResolvedAllocations(code, allocations, security) }
                                if (result.getOrDefault(false)) onDismiss()
                                else errorMessage = result.exceptionOrNull()?.let { com.kurogane.dividend.ui.networkErrorMessage(resources, it) }
                                    ?: resources.getString(R.string.roadmap_allocation_failed)
                            } finally { isRegistering = false }
                        }
                    },
                    enabled = !isLoading && !isRegistering && selectedHolding != null &&
                        selectedTickerInput.trim().equals(activeTicker, true),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(if (isRegistering) R.string.roadmap_registering else R.string.roadmap_text_40),
                        color = OnPrimary,
                        fontSize = AppTextSize.body,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

}

// Custom Account matrix allocation form Card component
@Composable
private fun AccountMatrixCard(
    title: String,
    isTaxFree: Boolean,
    usWithholdingEnabled: Boolean = true,
    shares: String,
    price: String,
    currency: String,
    onSharesChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    dps: Double
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val numShares = shares.toIntOrNull() ?: 0
    val unitPrice = price.toDoubleOrNull() ?: 0.0
    val totalCost = numShares * unitPrice
    val rawDividend = numShares * dps
    val usTax = currency == "$" && usWithholdingEnabled
    val finalDividend = com.kurogane.dividend.model.DividendTax.net(rawDividend, if (isTaxFree) 1.0 else 0.0, usTax)
    val taxAmt = if (isTaxFree) rawDividend * (if (usTax) .9 else 1.0) * .20315 else rawDividend - finalDividend

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundDark),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            // Header Row
            AdaptiveRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isTaxFree) PrimaryEmerald else SecondaryIndigo)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(title, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isTaxFree) PrimaryEmerald.copy(alpha = 0.15f) else Color(0x1F94A3B8)
                    ) {
                        Text(
                            text = if (isTaxFree) (if (usTax) resources.getString(R.string.roadmap_label_7) else resources.getString(R.string.roadmap_label_8)) else (if (usTax) resources.getString(R.string.roadmap_label_9) else resources.getString(R.string.roadmap_label_10)),
                            color = if (isTaxFree) PrimaryEmerald else OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.roadmap_text_58 , currency) + String.format(java.util.Locale.JAPAN, resources.getString(R.string.roadmap_label_11), finalDividend),
                    color = if (numShares > 0) PrimaryEmerald else OnSurfaceVariant,
                    fontSize = AppTextSize.dense,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Text Inputs Row
            AdaptiveRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shares Input Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.roadmap_text_59), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextField(
                            value = shares,
                            onValueChange = onSharesChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 28.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceContainerHigh,
                                unfocusedContainerColor = SurfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = AppTextSize.dense, textAlign = TextAlign.Center)
                        )
                        // Quick add buttons
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerHigh)
                                .clickable { onSharesChange(PortfolioMath.addShares(shares.toIntOrNull() ?: 0, 10).toString()) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(R.string.add_holding_text_13), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerHigh)
                                .clickable { onSharesChange(PortfolioMath.addShares(shares.toIntOrNull() ?: 0, 100).toString()) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(R.string.add_holding_text_14), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Acquisition Price Input Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.roadmap_text_60), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextField(
                            value = price,
                            onValueChange = onPriceChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 28.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceContainerHigh,
                                unfocusedContainerColor = SurfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = AppTextSize.dense, textAlign = TextAlign.Center)
                        )
                        Text(stringResource(R.string.roadmap_text_61), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }
                }
            }

            if (numShares > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.roadmap_text_62 , currency) + String.format(java.util.Locale.JAPAN, "%.2f", totalCost),
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense
                    )

                    if (isTaxFree) {
                        Text(
                            stringResource(R.string.roadmap_text_63 , currency) + String.format(java.util.Locale.JAPAN, "%.2f", taxAmt),
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            stringResource(R.string.roadmap_text_64 , currency) + String.format(java.util.Locale.JAPAN, "%.2f", taxAmt),
                            color = ErrorRed,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

