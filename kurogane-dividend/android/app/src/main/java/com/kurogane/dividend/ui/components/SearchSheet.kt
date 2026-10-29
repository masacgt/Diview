package com.kurogane.dividend.ui.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import com.kurogane.dividend.ui.networkErrorMessage
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
import com.kurogane.dividend.data.CloudflareSearchQuote
import kotlinx.coroutines.delay
import com.kurogane.dividend.data.runCatchingCancellable
import org.json.JSONArray
import org.json.JSONObject

data class CandidateStock(
    val code: String,
    val name: String,
    val desc: String,
    val price: String,
    val yieldRate: String,
    val cat: String,
    val isHigh: Boolean,
    val isNoDividendConfirmed: Boolean = false
)

private fun formatCandidatePrice(price: Double?, code: String): String? = price
    ?.takeIf { it.isFinite() && it > 0.0 }
    ?.let {
        "¥${com.kurogane.dividend.model.formatStockPriceDisplay(it, code)}"
    }

private fun formatCandidateYield(yieldRate: Double?, isNoDividendConfirmed: Boolean = false): String =
    if (yieldRate == null && !isNoDividendConfirmed) "未取得" else com.kurogane.dividend.model.formatDividendYieldDisplay(
        yieldRate?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: 0.0,
        isNoDividendConfirmed,
        java.util.Locale.JAPAN
    )

private const val RECENT_SEARCHES_PREFERENCES = "stock_search_history"
private const val RECENT_SEARCHES_KEY = "recent_candidates"
private const val MAX_RECENT_SEARCHES = 10

private fun loadRecentSearches(context: Context): List<CandidateStock> = runCatching {
    val rows = JSONArray(
        context.getSharedPreferences(RECENT_SEARCHES_PREFERENCES, Context.MODE_PRIVATE)
            .getString(RECENT_SEARCHES_KEY, "[]")
    )
    (0 until rows.length()).mapNotNull { index ->
        val row = rows.optJSONObject(index) ?: return@mapNotNull null
        CandidateStock(
            code = row.optString("code"),
            name = row.optString("name"),
            desc = row.optString("desc"),
            price = row.optString("price"),
            yieldRate = row.optString("yieldRate"),
            cat = row.optString("cat", "jp"),
            isHigh = row.optBoolean("isHigh"),
            isNoDividendConfirmed = row.optBoolean("isNoDividendConfirmed", false)
        ).takeIf { it.code.isNotBlank() && it.name.isNotBlank() }
    }
}.getOrDefault(emptyList())

private fun saveRecentSearches(context: Context, candidates: List<CandidateStock>) {
    val rows = JSONArray()
    candidates.forEach { candidate ->
        rows.put(
            JSONObject()
                .put("code", candidate.code)
                .put("name", candidate.name)
                .put("desc", candidate.desc)
                .put("price", candidate.price)
                .put("yieldRate", candidate.yieldRate)
                .put("cat", candidate.cat)
                .put("isHigh", candidate.isHigh)
                .put("isNoDividendConfirmed", candidate.isNoDividendConfirmed)
        )
    }
    context.getSharedPreferences(RECENT_SEARCHES_PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putString(RECENT_SEARCHES_KEY, rows.toString())
        .apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelectStock: (String) -> Unit,
    onSearch: suspend (String) -> List<CloudflareSearchHit> = { emptyList() },
    onLoadQuotes: suspend (List<String>) -> Map<String, CloudflareSearchQuote> = { emptyMap() }
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

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

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("all") } // "all", "jp", "us", "fund", "high-div"
    var remoteCandidates by remember { mutableStateOf<List<CandidateStock>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var recentCandidates by remember { mutableStateOf(loadRecentSearches(context)) }
    var recentQuotes by remember { mutableStateOf<Map<String, CloudflareSearchQuote>>(emptyMap()) }
    val resources = context.resources
    val search by rememberUpdatedState(onSearch)
    val loadQuotes by rememberUpdatedState(onLoadQuotes)
    val quoteCandidates = if (recentCandidates.isNotEmpty()) recentCandidates else candidates.filter { it.cat == "jp" }

    LaunchedEffect(visible) {
        if (visible) {
            searchQuery = ""
            selectedCategory = "all"
            recentCandidates = loadRecentSearches(context)
            recentQuotes = emptyMap()
        }
    }

    LaunchedEffect(searchQuery, visible, quoteCandidates, retryCount) {
        if (visible && normalizeStockSearchText(searchQuery.trim()).isEmpty() && quoteCandidates.isNotEmpty()) {
            recentQuotes = runCatchingCancellable {
                loadQuotes(quoteCandidates.map { it.code })
            }.getOrDefault(emptyMap())
        } else {
            recentQuotes = emptyMap()
        }
    }

    LaunchedEffect(searchQuery, visible, retryCount) {
        remoteCandidates = null
        isSearching = false
        searchError = null
        val query = normalizeStockSearchText(searchQuery.trim())
        if (!visible || query.isEmpty()) {
            remoteCandidates = null
            isSearching = false
        } else {
            delay(350)
            isSearching = true
            val result = runCatchingCancellable { search(query) }
            searchError = result.exceptionOrNull()?.let { networkErrorMessage(resources, it) }
            val hits = result.getOrNull()
            val quotesNeeded = hits.orEmpty()
                .map { it.code }
                .distinct()
            val searchQuotes = if (quotesNeeded.isEmpty()) {
                emptyMap()
            } else {
                runCatchingCancellable { loadQuotes(quotesNeeded) }.getOrDefault(emptyMap())
            }
            remoteCandidates = hits?.map { hit ->
                val local = candidates.firstOrNull { it.code.equals(hit.code, ignoreCase = true) }
                val quote = searchQuotes[hit.code.uppercase(java.util.Locale.ROOT)]
                val priceValue = quote?.price ?: hit.cachedPrice
                val price = formatCandidatePrice(priceValue, hit.code)
                val annualDividend = if (quote?.hasDividendStatus == true) quote.annualDividendPerShare
                    else quote?.annualDividendPerShare ?: hit.annualDividendPerShare
                val noDividendConfirmed = if (quote?.hasDividendStatus == true) {
                    quote.isNoDividendConfirmed
                } else {
                    hit.isNoDividendConfirmed
                }
                val yieldValue = annualDividend?.let { dps ->
                    priceValue?.takeIf { it > 0.0 }?.let { price -> dps / price * 100.0 }
                }?.takeIf { it.isFinite() && it in 0.0..100.0 }
                val yieldRate = formatCandidateYield(yieldValue ?: if (noDividendConfirmed) 0.0 else null, noDividendConfirmed)
                local?.copy(
                    name = hit.name,
                    desc = hit.industry?.let { "東証 • $it" } ?: local.desc,
                    price = price ?: "株価未取得",
                    yieldRate = yieldRate,
                    isHigh = yieldValue?.let { it >= 3.0 } ?: false,
                    isNoDividendConfirmed = noDividendConfirmed
                ) ?: CandidateStock(
                    code = hit.code,
                    name = hit.name,
                    desc = hit.industry?.let { "東証 • $it" } ?: "日本株",
                    price = price ?: "株価未取得",
                    yieldRate = yieldRate,
                    cat = "jp",
                    isHigh = yieldValue?.let { it >= 3.0 } ?: false,
                    isNoDividendConfirmed = noDividendConfirmed
                )
            }
            isSearching = false
        }
    }

    val filteredCandidates = remember(searchQuery, selectedCategory, remoteCandidates, recentCandidates, recentQuotes) {
        val query = normalizeStockSearchText(searchQuery.trim())
        val source = remoteCandidates ?: if (query.isEmpty()) {
            (if (recentCandidates.isNotEmpty()) recentCandidates else candidates).map { candidate ->
                val quote = recentQuotes[candidate.code.uppercase(java.util.Locale.ROOT)]
                val shouldRefreshQuote = recentCandidates.isNotEmpty() || candidate.cat == "jp"
                if (!shouldRefreshQuote) candidate else {
                    val yieldValue = quote?.annualDividendPerShare?.let { dps ->
                        quote?.price?.takeIf { it > 0.0 }?.let { dps / it * 100.0 }
                    }?.takeIf { it.isFinite() && it in 0.0..100.0 }
                    candidate.copy(
                        price = formatCandidatePrice(quote?.price, candidate.code) ?: "株価未取得",
                        yieldRate = formatCandidateYield(yieldValue ?: if (quote?.isNoDividendConfirmed == true) 0.0 else null,
                            quote?.isNoDividendConfirmed == true),
                        isHigh = yieldValue?.let { it >= 3.0 } ?: false,
                        isNoDividendConfirmed = quote?.isNoDividendConfirmed ?: candidate.isNoDividendConfirmed
                    )
                }
            }
        } else candidates.filter { item ->
            val matchesQuery = query.isEmpty() ||
                normalizeStockSearchText(item.code).contains(query) ||
                normalizeStockSearchText(item.name).contains(query)
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(SurfaceContainerLow)
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(16.dp)
        ) {
            val headerMaxHeight = maxHeight * 0.65f
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.fillMaxWidth().heightIn(max = headerMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                AdaptiveRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
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
                                stringResource(R.string.search_text),
                                color = OnSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = AppTextSize.body
                            )
                            Text(
                                stringResource(R.string.search_text_2),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize().size(32.dp)
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

                // Search Input Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.search_text_3),
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
                                    contentDescription = stringResource(R.string.search_a11y),
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
                        focusedBorderColor = FocusRingColor,
                        unfocusedBorderColor = ControlOutline,
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
                        "all" to stringResource(R.string.all),
                        "jp" to stringResource(R.string.japanese_stocks),
                        "us" to stringResource(R.string.us_stocks),
                        "fund" to stringResource(R.string.funds),
                        "high-div" to stringResource(R.string.search_high_dividend)
                    )
                    categories.forEach { (catId, catLabel) ->
                        val active = selectedCategory == catId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) PrimaryEmeraldContainer else SurfaceContainer)
                                .minimumInteractiveComponentSize().selectable(selected = active, role = androidx.compose.ui.semantics.Role.Tab) { selectedCategory = catId }
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

                // Candidate Count and List Header
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isSearching) resources.getString(R.string.search_label) else resources.getString(R.string.search_label_2, (filteredCandidates.size).toString()),
                        color = OnSurface,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.search_text_5),
                        color = PrimaryEmerald,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                }
                // Candidates scrollable list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("stock_search_results"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (searchError != null) item(key = "search_error") {
                        StatusMessage(resources.getString(R.string.search_error_with_fallback, searchError!!),
                            color = ErrorRed, actionLabel = resources.getString(R.string.retry), onAction = { retryCount++ })
                    }
                    if (!isSearching && filteredCandidates.isEmpty()) item(key = "search_empty") {
                        val emptyMessage = if (searchQuery.isBlank() && recentCandidates.isEmpty()) {
                            resources.getString(R.string.search_recent_empty)
                        } else {
                            resources.getString(R.string.search_empty)
                        }
                        StatusMessage(emptyMessage)
                    }
                    items(filteredCandidates, key = { it.code }) { stock ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceContainer)
                                .border(BorderStroke(0.5.dp, BorderColor), RoundedCornerShape(12.dp))
                                .clickable {
                                    val updatedRecent = (listOf(stock) + recentCandidates.filterNot {
                                        it.code.equals(stock.code, ignoreCase = true)
                                    }).take(MAX_RECENT_SEARCHES)
                                    recentCandidates = updatedRecent
                                    saveRecentSearches(context, updatedRecent)
                                    onSelectStock(stock.code)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Code box
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 54.dp).heightIn(min = 28.dp).padding(4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BackgroundDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stock.code,
                                    color = OnSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense,
                                    fontFamily = AppNumericFontFamily
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
                                    fontFamily = AppNumericFontFamily
                                )
                                Text(
                                    if (stock.yieldRate == "再投資型") resources.getString(R.string.search_label_3) else resources.getString(R.string.search_label_4, (stock.yieldRate).toString()),
                                    color = if (stock.yieldRate == "再投資型") OnSurfaceVariant else PrimaryEmerald,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}
