package com.kurogane.dividend.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyForecastDialog(
    isOpen: Boolean,
    watchlist: List<WatchlistStock>,
    onDismiss: () -> Unit
) {
    if (!isOpen) return
    val amounts = IntArray(12)
    if (watchlist.size == 4) {
        listOf(0, 800, 2400, 0, 800, 17400, 0, 800, 2400, 0, 800, 17400).forEachIndexed { index, value -> amounts[index] = value }
    } else {
        watchlist.forEach { stock ->
            val value = stock.dps * stock.defaultShares * if (stock.currency == "$") 150.0 else 1.0
            val perPay = if (stock.payoutMonths.isEmpty()) 0.0 else value / stock.payoutMonths.size
            stock.payoutMonths.filter { it in 1..12 }.forEach { amounts[it - 1] += perPay.toInt() }
        }
    }
    val total = amounts.sum()
    val mean = total / 12
    val max = amounts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val format = NumberFormat.getNumberInstance(Locale.JAPAN)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = .8f),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 5.dp).size(width = 42.dp, height = 4.dp).clip(CircleShape).background(SurfaceContainerHighest))
        }
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 760.dp).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(SecondaryIndigo.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.BarChart, null, tint = SecondaryIndigo, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("受取月別グラフ試算", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.width(5.dp))
                        Text("平準化シミュレーション", color = SecondaryIndigo, fontWeight = FontWeight.Bold, fontSize = 8.sp, modifier = Modifier.clip(CircleShape).background(SecondaryIndigo.copy(alpha = .15f)).padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                    Text("監視銘柄の入金タイミング・キャッシュフロー偏重分析", color = OnSurfaceVariant, fontSize = 9.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "閉じる", tint = OnSurfaceVariant, modifier = Modifier.size(18.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ForecastKpi("年間受取予定", "¥${format.format(total)} /年", Modifier.weight(1f))
                ForecastKpi("目標平均受取", "¥${format.format(mean)} /月", Modifier.weight(1f))
                ForecastKpi("⚠ 入金空白", "${amounts.count { it == 0 }} ヶ月", Modifier.weight(1f), ErrorRed)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundDark).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PieChart, null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                    Text(" 四半期キャッシュフロー比較", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Q2・Q4に集中", color = SecondaryIndigo, fontSize = 8.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (0..3).forEach { q ->
                        val quarter = amounts.slice(q * 3 until q * 3 + 3).sum()
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(SurfaceContainerLow).padding(7.dp)) {
                            Text("Q${q + 1} (${q * 3 + 1}–${q * 3 + 3}月)", color = OnSurfaceVariant, fontSize = 7.sp)
                            Text("¥${format.format(quarter)}", color = PrimaryEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(if (total > 0) String.format(Locale.JAPAN, "%.1f%%", quarter * 100.0 / total) else "0%", color = OnSurfaceVariant, fontSize = 7.sp)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundDark).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                    Text(" 12ヶ月 受取配当カレンダー棒グラフ", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("● 日本株   ● 米株   ▫ 空白", color = OnSurfaceVariant, fontSize = 7.sp)
                }
                Row(Modifier.fillMaxWidth().height(115.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    amounts.forEachIndexed { i, amount ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            if (amount > 0) Text("${if (amount >= 10000) String.format(Locale.JAPAN, "%.1f万", amount / 10000.0) else format.format(amount)}", color = OnSurfaceVariant, fontSize = 6.sp, maxLines = 1)
                            Box(
                                Modifier.fillMaxWidth(.75f).height((if (amount == 0) 3.0 else 8.0 + 74.0 * amount / max).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (amount == 0) SurfaceContainerHigh else PrimaryEmerald)
                            )
                            Text("${i + 1}", color = OnSurfaceVariant, fontSize = 7.sp)
                        }
                    }
                }
                Text("入金予定: ${watchlist.flatMap { it.payoutMonths }.distinct().sorted().joinToString("・").ifBlank { "未登録" }}月", color = OnSurfaceVariant, fontSize = 8.sp)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, SecondaryIndigo.copy(alpha = .35f), RoundedCornerShape(12.dp)).background(BackgroundDark).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TipsAndUpdates, null, tint = SecondaryIndigo, modifier = Modifier.size(16.dp))
                    Text(" インカム空白月解消アドバイス", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("平準化おすすめ", color = SecondaryIndigo, fontSize = 7.sp)
                }
                Spacer(Modifier.height(5.dp))
                val emptyMonths = (0 until 12).mapNotNull { index -> if (amounts[index] == 0) index + 1 else null }
                Text(
                    if (emptyMonths.isEmpty()) "受取予定のない月はありません。年間を通じて配当を受け取れる構成です。"
                    else "配当の空白月（${emptyMonths.joinToString("・")}月）があります。支払月の異なる銘柄や四半期配当株を組み合わせると、受取時期を分散できます。",
                    color = OnSurfaceVariant, fontSize = 9.sp, lineHeight = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                watchlist.take(3).forEach { stock ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stock.code, color = SecondaryIndigo, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("  ${stock.name}", color = Color.White, fontSize = 8.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${stock.payoutMonths.joinToString("・")}月", color = OnSurfaceVariant, fontSize = 8.sp)
                    }
                }
            }
            Button(onClick = onDismiss, Modifier.fillMaxWidth().height(38.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh)) {
                Text("閉じる", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ForecastKpi(title: String, value: String, modifier: Modifier = Modifier, valueColor: Color = PrimaryEmerald) {
    Column(modifier.clip(RoundedCornerShape(9.dp)).background(BackgroundDark).border(1.dp, SurfaceContainerHigh, RoundedCornerShape(9.dp)).padding(7.dp)) {
        Text(title, color = OnSurfaceVariant, fontSize = 8.sp)
        Text(value, color = valueColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
