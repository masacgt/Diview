package com.kurogane.dividend.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.WatchlistStock
import java.util.UUID

@Composable
fun AddHoldingDialog(onDismiss: () -> Unit, onSave: (Holding) -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var shares by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var dps by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.JAPAN) }
    val quantity = shares.toIntOrNull() ?: 0
    val currentPrice = price.toDoubleOrNull() ?: 0.0
    val acquisitionPrice = cost.toDoubleOrNull() ?: 0.0
    val annualDps = dps.toDoubleOrNull() ?: 0.0
    val market = when (category) {
        Category.JAPAN -> "日本株"
        Category.US -> "米国株"
        Category.FUND -> "投資信託"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保有資産を登録") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategorySelector(category) { category = it }
                EntryField("銘柄コード", code) { code = it }
                EntryField("銘柄名", name) { name = it }
                EntryField("保有数量", shares, numeric = true) { shares = it }
                EntryField("現在価格（1株・1口あたり）", price, numeric = true) { price = it }
                EntryField("取得単価", cost, numeric = true) { cost = it }
                EntryField("年間配当（1株あたり・無分配は0）", dps, numeric = true) { dps = it }
                EntryField("口座（例：特定口座、新NISA成長投資枠）", account) { account = it }
                Text("登録内容はこの端末内に保存します。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.isNotBlank() && quantity > 0 && currentPrice > 0.0 && acquisitionPrice > 0.0 && account.isNotBlank(),
                onClick = {
                    val currency = if (category == Category.US) "$" else "¥"
                    onSave(
                        Holding(
                            id = UUID.randomUUID().toString(),
                            code = code.trim(),
                            name = name.trim().ifBlank { code.trim() },
                            sector = "未分類",
                            market = market,
                            category = category,
                            price = currentPrice,
                            currency = currency,
                            shares = quantity,
                            acqPrice = acquisitionPrice,
                            dps = annualDps,
                            yieldRate = if (currentPrice > 0.0) annualDps / currentPrice * 100.0 else 0.0,
                            yoc = if (acquisitionPrice > 0.0) annualDps / acquisitionPrice * 100.0 else 0.0,
                            nextPayoutTiming = "予定データなし",
                            nextPayoutAmount = "—",
                            dividendMonths = emptyList(),
                            dividendType = "未登録",
                            accountType = account.trim(),
                            dailyChange = "更新データなし",
                            isPositive = true,
                            fxRateToJpy = if (category == Category.US) 0.0 else 1.0
                        )
                    )
                }
            ) { Text("登録") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
fun AddWatchlistDialog(
    onDismiss: () -> Unit,
    onSave: (WatchlistStock) -> Boolean
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var dps by remember { mutableStateOf("") }
    var targetYield by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.JAPAN) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentPrice = price.toDoubleOrNull() ?: 0.0
    val annualDps = dps.toDoubleOrNull() ?: 0.0
    val target = targetYield.toDoubleOrNull() ?: 0.0
    val market = when (category) {
        Category.JAPAN -> "日本株"
        Category.US -> "米国株"
        Category.FUND -> "投資信託"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watchlistに追加") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategorySelector(category) { category = it }
                EntryField("銘柄コード", code) { code = it; error = null }
                EntryField("銘柄名", name) { name = it }
                EntryField("現在価格（不明なら空欄）", price, numeric = true) { price = it }
                EntryField("年間配当（1株あたり・不明なら空欄）", dps, numeric = true) { dps = it }
                EntryField("目標利回り（%）", targetYield, numeric = true) { targetYield = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = code.isNotBlank() && target > 0.0, onClick = {
                val currency = if (category == Category.US) "$" else "¥"
                val stock = WatchlistStock(
                    code = code.trim(),
                    name = name.trim().ifBlank { code.trim() },
                    market = market,
                    sector = "未分類",
                    category = when (category) {
                        Category.JAPAN -> "jp"
                        Category.US -> "us"
                        Category.FUND -> "fund"
                    },
                    price = currentPrice,
                    currency = currency,
                    dps = annualDps,
                    currentYield = if (currentPrice > 0.0) annualDps / currentPrice * 100.0 else 0.0,
                    targetYield = target,
                    targetPrice = if (annualDps > 0.0) annualDps / (target / 100.0) else 0.0,
                    dailyChange = "更新データなし",
                    isPositive = true,
                    payoutMonths = emptyList(),
                    payoutTiming = "予定データなし",
                    desc = market,
                    alertEnabled = false,
                    badge = null
                )
                if (onSave(stock)) onDismiss() else error = "Watchlistに同じ銘柄が登録済みです。"
            }) { Text("追加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
fun RegisterWatchlistHoldingDialog(
    stock: WatchlistStock,
    onDismiss: () -> Unit,
    onRegister: (shares: Int, acquisitionPrice: Double, accountType: String) -> Unit
) {
    var shares by remember(stock.code) { mutableStateOf("") }
    var acquisition by remember(stock.code) { mutableStateOf("") }
    var account by remember(stock.code) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保有資産として登録") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${stock.name}（${stock.code}）を保有資産へ移します。", style = MaterialTheme.typography.bodySmall)
                EntryField("保有数量", shares, numeric = true) { shares = it }
                EntryField("取得単価", acquisition, numeric = true) { acquisition = it }
                EntryField("口座", account) { account = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (shares.toIntOrNull() ?: 0) > 0 && (acquisition.toDoubleOrNull() ?: 0.0) > 0.0 && account.isNotBlank(),
                onClick = { onRegister(shares.toInt(), acquisition.toDouble(), account.trim()) }
            ) { Text("登録") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
private fun CategorySelector(selected: Category, onSelect: (Category) -> Unit) {
    val choices = listOf(Category.JAPAN to "日本株", Category.US to "米国株", Category.FUND to "投資信託")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        choices.forEach { (category, label) ->
            FilterChip(selected = selected == category, onClick = { onSelect(category) }, label = { Text(label) })
        }
    }
}

@Composable
private fun EntryField(label: String, value: String, numeric: Boolean = false, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = if (numeric) androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal) else androidx.compose.foundation.text.KeyboardOptions.Default
    )
}
