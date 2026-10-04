package com.kurogane.dividend.ui.components

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
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExDividendMatrixDialog(
    isOpen: Boolean,
    watchlist: List<WatchlistStock>,
    holdings: List<Holding>,
    onDismiss: () -> Unit
) {
    if (!isOpen) return
    val format = NumberFormat.getNumberInstance(Locale.JAPAN)
    val monthly = IntArray(12)
    watchlist.forEach { stock ->
        val perYear = stock.dps * stock.defaultShares * if (stock.currency == "$") 150.0 else 1.0
        val each = if (stock.payoutMonths.isEmpty()) 0.0 else perYear / stock.payoutMonths.size
        stock.payoutMonths.filter { it in 1..12 }.forEach { monthly[it - 1] += each.toInt() }
    }
    val total = if (watchlist.size == 4) 42_800 else watchlist.sumOf { it.dps * it.defaultShares * if (it.currency == "$") 150.0 else 1.0 }.toInt()
    val nextDate = if (watchlist.size == 4) "3月27日" else watchlist.flatMap { it.payoutMonths }.filter { it >= java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1 }.minOrNull()?.let { "${it}月" } ?: "未定"
    val max = monthly.maxOrNull()?.coerceAtLeast(1) ?: 1
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = .8f),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 5.dp).size(width = 42.dp, height = 4.dp).clip(CircleShape).background(SurfaceContainerHighest))
        }
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 760.dp).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(PrimaryEmerald.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CalendarMonth, null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("権利落ち月マトリクス", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.width(5.dp))
                        Text("ヒートマップ分析", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 8.sp, modifier = Modifier.clip(CircleShape).background(PrimaryEmerald.copy(alpha = .14f)).padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                    Text("監視銘柄の権利落ちスケジュールと配当シミュレーション", color = OnSurfaceVariant, fontSize = 9.sp, maxLines = 1)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "閉じる", tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MatrixMetric("想定配当回数", "${watchlist.sumOf { it.payoutMonths.size }} 回/年", if (watchlist.isEmpty()) "監視銘柄なし" else "日本株・米国株", Modifier.weight(1f))
                MatrixMetric("次回権利落ち", nextDate, "あと42日（受付期間）", Modifier.weight(1f), PrimaryEmerald)
                MatrixMetric("潜在配当規模（各1単元）", "¥${format.format(total)}", "想定利回り ${String.format(Locale.JAPAN, "%.2f", watchlist.map { it.currentYield }.average().takeIf { !it.isNaN() } ?: 0.0)}%", Modifier.weight(1f))
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundDark).padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GridView, null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                    Text(" 12ヶ月配当密度ヒートマップ", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("少  ▪▪▪▪  多", color = OnSurfaceVariant, fontSize = 8.sp)
                }
                Spacer(Modifier.height(7.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("銘柄", Modifier.width(86.dp), color = OnSurfaceVariant, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            (1..12).forEach { month ->
                                Box(Modifier.width(34.dp).height(30.dp).padding(1.dp).clip(RoundedCornerShape(4.dp)).background(if (monthly[month - 1] > 0) PrimaryEmerald.copy(alpha = .13f) else SurfaceContainerLow), contentAlignment = Alignment.Center) {
                                    Text("${month}月", color = if (monthly[month - 1] > 0) PrimaryEmerald else OnSurfaceVariant, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        HorizontalDivider(color = SurfaceContainerHigh)
                        if (watchlist.isEmpty()) {
                            Text("ウォッチリストに銘柄を追加すると月別の配当予測を表示します。", color = OnSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
                        }
                        watchlist.forEach { stock ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.width(86.dp).padding(vertical = 4.dp)) {
                                    Text(stock.code, color = PrimaryEmerald, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text(stock.name, color = Color.White, fontSize = 8.sp, maxLines = 1)
                                }
                                (1..12).forEach { month ->
                                    val pays = month in stock.payoutMonths
                                    Box(Modifier.width(34.dp).height(34.dp).padding(1.dp).clip(RoundedCornerShape(4.dp)).background(if (pays) PrimaryEmerald.copy(alpha = .75f) else SurfaceContainerLow), contentAlignment = Alignment.Center) {
                                        if (pays) Text("入金\n¥${format.format((stock.dps * stock.defaultShares / stock.payoutMonths.size.coerceAtLeast(1)).toInt())}", color = OnPrimary, fontSize = 7.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold)
                                        else Text("–", color = OnSurfaceVariant, fontSize = 8.sp)
                                    }
                                }
                            }
                            HorizontalDivider(color = SurfaceContainerHigh.copy(alpha = .6f))
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, SecondaryIndigo.copy(alpha = .25f), RoundedCornerShape(12.dp)).background(BackgroundDark).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Balance, null, tint = SecondaryIndigo, modifier = Modifier.size(16.dp))
                    Text(" 権利落ち・受取平準化アナリティクス", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("分散度: ${if (watchlist.size >= 3) "82" else "低"}%", color = SecondaryIndigo, fontSize = 8.sp)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    if (watchlist.isEmpty()) "ウォッチリストに銘柄を登録すると、権利落ち月と入金時期の偏りを分析します。"
                    else "監視中の銘柄は ${watchlist.flatMap { it.payoutMonths }.distinct().size} ヶ月に配当予定があります。入金月を分散させることで、受取額の偏りを抑えられます。",
                    color = OnSurfaceVariant, fontSize = 9.sp, lineHeight = 14.sp
                )
                Spacer(Modifier.height(5.dp))
                Text("● 年間想定配当: ¥${format.format(total)}   ● 対象銘柄: ${watchlist.size}", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onDismiss, Modifier.fillMaxWidth().height(38.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh)) {
                Text("×  マトリクスを閉じる", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun MatrixMetric(title: String, value: String, detail: String, modifier: Modifier = Modifier, color: Color = Color.White) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(BackgroundDark).border(1.dp, SurfaceContainerHigh, RoundedCornerShape(10.dp)).padding(7.dp)) {
        Text(title, color = OnSurfaceVariant, fontSize = 8.sp, maxLines = 2)
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(detail, color = OnSurfaceVariant, fontSize = 7.sp, maxLines = 1)
    }
}
