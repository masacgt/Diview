package com.kurogane.dividend.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.data.SecurityCatalogRepository
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.util.Locale

private data class SearchCandidate(
    val code: String,
    val name: String,
    val category: String,
    val description: String,
    val price: Double?,
    val currency: String,
    val dividendYield: Double?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySearchSheet(viewModel: DividendViewModel, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val catalog = remember(context) { SecurityCatalogRepository(context).securities }
    val holdings by viewModel.holdings.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val shared by viewModel.sharedSecurities.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    val localCandidates = remember(holdings, watchlist, shared) {
        val owned = holdings.map { holding ->
            SearchCandidate(
                holding.code, holding.name,
                when (holding.category) { Category.JAPAN -> "jp"; Category.US -> "us"; Category.FUND -> "fund" },
                listOf(holding.market, holding.sector).filter(String::isNotBlank).joinToString(" ・ "),
                holding.price.takeIf { it > 0.0 }, holding.currency,
                holding.currentYield.takeIf { holding.price > 0.0 && holding.dps > 0.0 }
            )
        }
        val watched = watchlist.map { stock ->
            SearchCandidate(
                stock.code, stock.name, stock.category,
                listOf(stock.market, stock.sector).filter(String::isNotBlank).joinToString(" ・ "),
                stock.price.takeIf { it > 0.0 }, stock.currency,
                stock.currentYield.takeIf { stock.price > 0.0 && stock.dps > 0.0 }
            )
        }
        (owned + watched).distinctBy { it.code.uppercase(Locale.ROOT) }
    }
    val candidates = remember(catalog, localCandidates, shared) {
        val localCodes = localCandidates.map { it.code.uppercase(Locale.ROOT) }.toSet()
        localCandidates + catalog.asSequence()
            .filterNot { it.code.uppercase(Locale.ROOT) in localCodes }
            .map { item ->
                val quote = shared[item.code]
                val price = quote?.price?.takeIf { it > 0.0 }
                val yield = if (price != null && (quote.annualDps ?: 0.0) > 0.0) quote.annualDps!! / price * 100.0 else null
                SearchCandidate(item.code, item.name, "jp", "日本株", price, "¥", yield)
            }.toList()
    }
    val normalized = query.trim().lowercase(Locale.ROOT)
    val filtered = remember(candidates, normalized, filter) {
        val matches = if (normalized.isBlank()) {
            (localCandidates + candidates.filter { it.code in setOf("7203", "8316", "9432", "8058") })
                .distinctBy { it.code.uppercase(Locale.ROOT) }
        } else candidates.filter { item ->
            (normalized.length >= 2 && item.name.lowercase(Locale.ROOT).contains(normalized)) ||
                (normalized.length >= 3 && item.code.lowercase(Locale.ROOT).contains(normalized))
        }
        matches.filter { item ->
            filter == "all" || (filter == "high" && (item.dividendYield ?: 0.0) >= 3.0) || item.category == filter
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.78f)
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 730.dp).padding(horizontal = 16.dp)) {
            SheetTitle(Icons.Default.Search, "銘柄検索・絞り込み", "銘柄名やコードで検索し、詳細分析を開く", onDismiss)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "検索語を消去") } }) else null,
                placeholder = { Text("銘柄名は2文字、コードは3文字から検索", fontSize = 12.sp) },
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "すべて", "jp" to "日本株", "us" to "米国株", "fund" to "投信", "high" to "高配当 (3%以上)").forEach { (id, label) ->
                    FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(label, fontSize = 11.sp) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("注目の主力銘柄", color = PrimaryEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("7203", "8316", "9432", "8058").forEach { code ->
                    SuggestionChip(onClick = { query = code }, label = { Text(code, fontSize = 11.sp) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (normalized.isBlank()) "候補銘柄プレビュー (${filtered.take(3).size}件保有中)" else "候補銘柄 (${filtered.size}件)", color = OnSurfaceVariant, fontSize = 11.sp)
                Text("タップで詳細を表示", color = PrimaryEmerald, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            if (normalized.isBlank()) "候補銘柄はありません。名前またはコードを入力してください。"
                            else if (normalized.length < 2) "銘柄名は2文字、コードは3文字から検索できます。"
                            else "該当する銘柄がありません。",
                            color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp)
                        )
                    }
                }
                items(filtered.take(if (normalized.isBlank()) 3 else 50), key = { it.code }) { candidate ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(BackgroundDark, RoundedCornerShape(12.dp))
                            .clickable {
                                if (candidate.category == "jp") viewModel.loadSecurityForDetail(candidate.code)
                                onSelect(candidate.code)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(candidate.code, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(candidate.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(candidate.description, color = OnSurfaceVariant, fontSize = 10.sp, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(candidate.price?.let { candidate.currency + String.format(Locale.JAPAN, "%,.0f", it) } ?: "価格未取得", color = Color.White, fontSize = 11.sp)
                            candidate.dividendYield?.let { Text(String.format(Locale.JAPAN, "利回り %.2f%%", it), color = PrimaryEmerald, fontSize = 10.sp) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("閉じる") }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistorySheet(
    viewModel: DividendViewModel,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onOpenWatchlist: () -> Unit
) {
    val history by viewModel.notificationHistory.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    var filter by remember { mutableStateOf("all") }
    LaunchedEffect(Unit) { viewModel.refreshNotificationHistory() }
    val unread = history.count { it.unread }
    val visible = history.filter { filter == "all" || it.category == filter }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.78f)
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 730.dp).padding(horizontal = 16.dp)) {
            SheetTitle(Icons.Default.NotificationsActive, "通知履歴・アクティビティ", "実際に発生した買場シグナル通知", onDismiss)
            if (unread > 0) Text("未読 ${unread}件", color = PrimaryEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "すべて", "dividend" to "権利落ち・入金", "signal" to "買場シグナル").forEach { (id, label) ->
                    FilterChip(selected = filter == id, onClick = { filter = id }, label = {
                        Text("$label (${if (id == "all") history.size else history.count { it.category == id }})", fontSize = 11.sp)
                    })
                }
            }
            if (unread > 0) {
                TextButton(onClick = viewModel::markAllNotificationsRead, modifier = Modifier.align(Alignment.End)) {
                    Text("すべて既読", fontSize = 11.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().background(BackgroundDark, RoundedCornerShape(8.dp)).padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("買場アラート: ${if (watchlist.any { it.alertEnabled }) "ON" else "OFF"}", color = PrimaryEmerald, fontSize = 11.sp)
                Text("即時プッシュ", color = OnSurfaceVariant, fontSize = 10.sp)
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (visible.isEmpty()) item {
                    Text("表示できる通知はありません。", color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(vertical = 20.dp))
                }
                items(visible, key = { it.id }) { item ->
                    Column(
                        Modifier.fillMaxWidth()
                            .background(if (item.unread) SurfaceContainer else BackgroundDark, RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.markNotificationRead(item.id)
                                if (item.code.isNotBlank()) onSelect(item.code)
                            }.padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.unread) {
                                Text("●", color = PrimaryEmerald, fontSize = 10.sp)
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(item.title, color = PrimaryEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text(item.code, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text(item.name, modifier = Modifier.weight(1f), color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(DateUtils.getRelativeTimeSpanString(item.createdAt).toString(), color = OnSurfaceVariant, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(item.body, color = OnSurface, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onOpenWatchlist) { Text("Watchlistで通知設定", fontSize = 11.sp) }
                TextButton(onClick = onDismiss) { Text("閉じる") }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SheetTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PrimaryEmerald)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = OnSurfaceVariant, fontSize = 10.sp)
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "閉じる", tint = OnSurfaceVariant) }
    }
}
