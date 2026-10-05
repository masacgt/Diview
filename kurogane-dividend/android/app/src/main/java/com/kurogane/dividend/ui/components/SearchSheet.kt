package com.kurogane.dividend.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.data.CloudflareSearchHit
import kotlinx.coroutines.delay
import com.kurogane.dividend.data.runCatchingCancellable

data class CandidateStock(
    val code: String,
    val name: String,
    val desc: String,
    val price: String,
    val yieldRate: String,
    val cat: String,
    val isHigh: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelectStock: (String) -> Unit,
    onSearch: suspend (String) -> List<CloudflareSearchHit> = { emptyList() }
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val candidates = remember {
        listOf(
            CandidateStock("7203", "トヨタ自動車", "東証P • 輸送用機器", "¥2,600", "2.80%", "jp", false),
            CandidateStock("8316", "三井住友FG", "東証P • 銀行業", "¥3,150", "4.13%", "jp", true),
            CandidateStock("9432", "日本電信電話 (NTT)", "東証P • 情報・通信業", "¥154.2", "3.37%", "jp", true),
            CandidateStock("8058", "三菱商事", "東証P • 卸売業", "¥3,240", "3.52%", "jp", true),
            CandidateStock("AAPL", "アップル", "NASDAQ • 米国株", "$220.00", "0.52%", "us", false),
            CandidateStock("MSFT", "マイクロソフト", "NASDAQ • ソフトウェア", "$440.00", "0.76%", "us", false),
            CandidateStock("eMAXIS", "eMAXIS Slim 全世界株式", "オルカン • 全世界株", "¥24,460", "再投資型", "fund", false)
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") } // "all", "jp", "us", "fund", "high-div"
    var remoteCandidates by remember { mutableStateOf<List<CandidateStock>?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, visible) {
        remoteCandidates = null
        isSearching = false
        val query = searchQuery.trim()
        if (!visible || query.isEmpty()) {
            remoteCandidates = null
            isSearching = false
        } else if (query.length >= 2 || query.any(Char::isDigit)) {
            delay(350)
            isSearching = true
            val result = runCatchingCancellable { onSearch(query) }
            remoteCandidates = result.getOrNull()?.map { hit ->
                val local = candidates.firstOrNull { it.code.equals(hit.code, ignoreCase = true) }
                val price = hit.cachedPrice?.let { "¥${java.text.NumberFormat.getNumberInstance(java.util.Locale.JAPAN).format(it.toLong())}" }
                local?.copy(
                    name = hit.name,
                    desc = hit.industry?.let { "東証 • $it" } ?: local.desc,
                    price = price ?: local.price
                ) ?: CandidateStock(
                    code = hit.code,
                    name = hit.name,
                    desc = hit.industry?.let { "東証 • $it" } ?: "日本株 • Cloudflare銘柄マスター",
                    price = price ?: "株価未取得",
                    yieldRate = "--",
                    cat = "jp",
                    isHigh = false
                )
            }
            isSearching = false
        } else {
            remoteCandidates = null
        }
    }

    val filteredCandidates = remember(searchQuery, selectedCategory, remoteCandidates) {
        val source = remoteCandidates ?: candidates.filter { item ->
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() || item.code.lowercase().contains(q) || item.name.lowercase().contains(q)
            matchesQuery
        }
        source.filter { item ->
            val matchesCat = when (selectedCategory) {
                "all" -> true
                "high-div" -> item.isHigh
                else -> item.cat == selectedCategory
            }
            matchesCat
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = SurfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(SurfaceContainerLow)
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Grab Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(48.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SurfaceContainerHighest)
                )

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
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
                                .background(SurfaceContainer)
                                .border(1.dp, SurfaceContainerHighest, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                "銘柄検索・絞り込み",
                                color = OnSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = AppTextSize.body
                            )
                            Text(
                                "保有銘柄の絞り込みまたは詳細分析を開く",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
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

                HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

                // Search Input Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "銘柄名・コード・ティッカー (例: 7203, トヨタ, AAPL)",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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

                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf(
                        "all" to "すべて",
                        "jp" to "日本株",
                        "us" to "米国株",
                        "fund" to "投信",
                        "high-div" to "高配当 (3%以上)"
                    )
                    categories.forEach { (catId, catLabel) ->
                        val active = selectedCategory == catId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) PrimaryEmeraldContainer else SurfaceContainer)
                                .clickable { selectedCategory = catId }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = catLabel,
                                color = if (active) OnPrimaryContainer else OnSurfaceVariant,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }
                }

                // Quick hot search tags
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "注目の高配当・主力銘柄",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val hotTags = listOf("7203 トヨタ", "8316 三井住友FG", "9432 NTT", "8058 三菱商事", "AAPL アップル")
                        hotTags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainer)
                                    .clickable {
                                        val codeOnly = tag.split(" ").first()
                                        searchQuery = codeOnly
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    tag,
                                    color = PrimaryEmerald,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Candidate Count and List Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isSearching) "Cloudflare銘柄マスターを検索中…" else "候補銘柄 (${filteredCandidates.size}件)",
                        color = OnSurface,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "タップで銘柄詳細を表示",
                        color = PrimaryEmerald,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Candidates scrollable list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("stock_search_results"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCandidates) { stock ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceContainer)
                                .border(BorderStroke(0.5.dp, BorderColor), RoundedCornerShape(12.dp))
                                .clickable { onSelectStock(stock.code) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Code box
                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BackgroundDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stock.code,
                                    color = OnSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Name and details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stock.name,
                                    color = OnSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.body
                                )
                                Text(
                                    stock.desc,
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            }

                            // Price and Yield
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stock.price,
                                    color = OnSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.body,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    if (stock.yieldRate == "再投資型") "再投資型" else "利回り ${stock.yieldRate}",
                                    color = if (stock.yieldRate == "再投資型") OnSurfaceVariant else PrimaryEmerald,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense
                                )
                            }
                        }
                    }
                }

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "閉じる",
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = AppTextSize.body
                    )
                }
            }
        }
    }
}
