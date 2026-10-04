package com.kurogane.dividend.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*

data class CandidateStock(
    val code: String,
    val name: String,
    val desc: String,
    val price: String,
    val yieldRate: String,
    val cat: String,
    val isHigh: Boolean
)

@Composable
fun SearchSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelectStock: (String) -> Unit
) {
    if (!visible) return

    val candidates = remember {
        listOf(
            CandidateStock("7203", "トヨタ自動車", "東証P • 輸送用機器", "¥2,600", "2.80%", "jp", false),
            CandidateStock("8316", "三井住友FG", "東証P • 銀行業", "¥3,150", "4.13%", "jp", true),
            CandidateStock("9432", "日本電信電話 (NTT)", "東証P • 情報通信", "¥154.2", "3.37%", "jp", true),
            CandidateStock("8058", "三菱商事", "東証P • 卸売業", "¥3,240", "3.52%", "jp", true),
            CandidateStock("AAPL", "Apple Inc.", "NASDAQ • 米国株", "$220.00", "0.52%", "us", false),
            CandidateStock("MSFT", "Microsoft Corp.", "NASDAQ • ソフトウェア", "$440.00", "0.76%", "us", false),
            CandidateStock("eMAXIS", "eMAXIS Slim 全世界株式", "オルカン • 全世界株", "¥24,460", "再投資型", "fund", false)
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") } // "all", "jp", "us", "fund", "high-div"

    val filteredCandidates = remember(searchQuery, selectedCategory) {
        candidates.filter { item ->
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() || item.code.lowercase().contains(q) || item.name.lowercase().contains(q)
            val matchesCat = when (selectedCategory) {
                "all" -> true
                "high-div" -> item.isHigh
                else -> item.cat == selectedCategory
            }
            matchesQuery && matchesCat
        }
    }

    // Scrim + Slide Up animated container
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
        // Prevent click propagation to scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
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
                        .background(Color(0xFF334155))
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
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
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
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "保有銘柄の絞り込みまたは詳細分析を開く",
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

                // Search Input Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "銘柄名・コード・ティッカー (例: 7203, トヨタ, AAPL)",
                            color = OnSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 11.sp
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
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
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
                                color = if (active) OnPrimary else OnSurfaceVariant,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 11.sp
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
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val hotTags = listOf("7203 トヨタ", "8316 三井住友FG", "9432 NTT", "8058 三菱商事", "AAPL Apple")
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
                                    fontSize = 11.sp,
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
                        "候補銘柄 (${filteredCandidates.size}件)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "タップで銘柄詳細を表示",
                        color = PrimaryEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Candidates scrollable list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCandidates) { stock ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceContainer)
                                .border(BorderStroke(0.5.dp, Color(0xFF1E293B)), RoundedCornerShape(12.dp))
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
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Name and details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stock.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    stock.desc,
                                    color = OnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }

                            // Price and Yield
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stock.price,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    if (stock.yieldRate == "再投資型") "再投資型" else "利回り ${stock.yieldRate}",
                                    color = if (stock.yieldRate == "再投資型") OnSurfaceVariant else PrimaryEmerald,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
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
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
