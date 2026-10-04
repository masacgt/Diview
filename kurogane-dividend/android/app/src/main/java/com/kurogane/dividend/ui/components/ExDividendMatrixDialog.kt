package com.kurogane.dividend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kurogane.dividend.ui.theme.*

@Composable
fun ExDividendMatrixDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = PrimaryEmerald
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "年間権利落ち・配当金カレンダー",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface
                                )
                            )
                            Text(
                                "12ヶ月配当密度ヒートマップ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 12 Months Heatmap Grid
                val months = (1..12).toList()
                val dividendStrengths = listOf(1, 2, 4, 1, 2, 5, 1, 2, 3, 1, 2, 5)

                Text(
                    "月別受取密度",
                    style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    months.zip(dividendStrengths).forEach { (month, strength) ->
                        val bg = when (strength) {
                            5 -> PrimaryEmerald
                            4 -> PrimaryEmerald.copy(alpha = 0.7f)
                            3 -> PrimaryEmerald.copy(alpha = 0.5f)
                            2 -> PrimaryEmerald.copy(alpha = 0.3f)
                            else -> SurfaceContainerHigh
                        }
                        val textColor = if (strength >= 4) Color.Black else OnSurface

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "${month}月",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Stocks breakdown table
                Text(
                    "銘柄別 権利付最終日 / 入金月",
                    style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceContainerHigh)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MatrixRow("7203 トヨタ自動車", "3月 / 9月末", "6月 / 12月入金", "¥7,280")
                    Divider(color = SurfaceContainerHighest)
                    MatrixRow("8316 三井住友FG", "3月 / 9月末", "6月 / 12月入金", "¥13,000")
                    Divider(color = SurfaceContainerHighest)
                    MatrixRow("9432 日本電信電話", "3月 / 9月末", "6月 / 12月入金", "¥520")
                    Divider(color = SurfaceContainerHighest)
                    MatrixRow("AAPL Apple Inc.", "2, 5, 8, 11月", "各月中旬 (四半期)", "$10.0")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("閉じる", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MatrixRow(
    title: String,
    rightsMonth: String,
    payoutTiming: String,
    expectedAmount: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall.copy(color = OnSurface, fontWeight = FontWeight.SemiBold))
            Text("$rightsMonth → $payoutTiming", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, fontSize = 10.sp))
        }
        Text(
            expectedAmount,
            style = MaterialTheme.typography.bodySmall.copy(
                color = PrimaryEmerald,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}
