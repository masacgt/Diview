package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import com.kurogane.dividend.ui.components.AdaptiveTopAppBar
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsPrivacyScreen(
    onBack: () -> Unit
) {
    var activeTab by remember { mutableStateOf("privacy") } // "terms" or "privacy"
    var openArt1 by remember { mutableStateOf(true) }
    var openArt2 by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.terms_privacy_text), color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = OnSurface)
                    }
                },
                containerColor = SpaceNavy
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Security Core Status Badge Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdaptiveRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryEmerald)
                            )
                            Text(stringResource(R.string.terms_privacy_diview_security_core), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(PrimaryEmeraldContainer.copy(alpha = 0.15f))
                                .border(0.5.dp, PrimaryEmeraldContainer.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(10.dp))
                                Text(stringResource(R.string.terms_privacy_text_2), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Grid details
                    AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // AES-256 Box
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                            border = BorderStroke(0.5.dp, BorderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(stringResource(R.string.terms_privacy_text_3), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.terms_privacy_text_4), color = SoftGray, fontSize = AppTextSize.dense)
                                }
                            }
                        }

                        // Anti-tracking Box
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                            border = BorderStroke(0.5.dp, BorderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(stringResource(R.string.terms_privacy_text_5), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.terms_privacy_text_6), color = SoftGray, fontSize = AppTextSize.dense)
                                }
                            }
                        }
                    }
                }
            }

            // Segment Tab Switcher Row
            AdaptiveRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BackgroundDark)
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Terms Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTab == "terms") PrimaryEmerald else Color.Transparent)
                        .clickable { activeTab = "terms" },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = null,
                            tint = if (activeTab == "terms") OnPrimary else SoftGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            stringResource(R.string.terms_privacy_text_7),
                            color = if (activeTab == "terms") OnPrimary else SoftGray,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Privacy Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTab == "privacy") PrimaryEmerald else Color.Transparent)
                        .clickable { activeTab = "privacy" },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = if (activeTab == "privacy") OnPrimary else SoftGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            stringResource(R.string.terms_privacy_text_8),
                            color = if (activeTab == "privacy") OnPrimary else SoftGray,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tabs content
            if (activeTab == "terms") {
                // TERMS CONTENT
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdaptiveRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.terms_privacy_text_9), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PrimaryEmeraldContainer.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.terms_privacy_text_10), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                stringResource(R.string.terms_privacy_text_11),
                                color = SoftGray,
                                fontSize = AppTextSize.caption,
                                lineHeight = AppTextLineHeight.body
                            )
                        }
                    }

                    // Article 1
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column {
                            AdaptiveRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openArt1 = !openArt1 }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.terms_privacy_text_12), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Icon(
                                    imageVector = if (openArt1) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = SoftGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (openArt1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BackgroundDark.copy(alpha = 0.5f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.terms_privacy_text_13),
                                        color = SoftGray,
                                        fontSize = AppTextSize.caption,
                                        lineHeight = AppTextLineHeight.body
                                    )
                                }
                            }
                        }
                    }

                    // Article 2 (Exclusion of liability)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column {
                            AdaptiveRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openArt2 = !openArt2 }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.terms_privacy_text_14), color = ErrorRed, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Icon(
                                    imageVector = if (openArt2) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (openArt2) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BackgroundDark.copy(alpha = 0.5f))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.terms_privacy_text_15),
                                        color = SoftGray,
                                        fontSize = AppTextSize.caption,
                                        lineHeight = AppTextLineHeight.body
                                    )
                                    Text(
                                        stringResource(R.string.terms_privacy_text_16),
                                        color = SoftGray,
                                        fontSize = AppTextSize.caption,
                                        lineHeight = AppTextLineHeight.body
                                    )
                                }
                            }
                        }
                    }

                    // Article 3
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.terms_privacy_text_17), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.terms_privacy_text_18),
                                color = SoftGray,
                                fontSize = AppTextSize.caption,
                                lineHeight = AppTextLineHeight.body
                            )
                            HorizontalDivider(color = BorderColor)
                            Text(stringResource(R.string.terms_privacy_text_30), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.terms_privacy_text_31), color = SoftGray, fontSize = AppTextSize.caption, lineHeight = AppTextLineHeight.body)
                            HorizontalDivider(color = BorderColor)
                            Text(stringResource(R.string.terms_privacy_text_32), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.terms_privacy_text_33), color = SoftGray, fontSize = AppTextSize.caption, lineHeight = AppTextLineHeight.body)
                            HorizontalDivider(color = BorderColor)
                            Text(stringResource(R.string.terms_privacy_text_34), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.terms_privacy_text_35), color = SoftGray, fontSize = AppTextSize.caption, lineHeight = AppTextLineHeight.body)
                            HorizontalDivider(color = BorderColor)
                            Text(stringResource(R.string.terms_privacy_text_36), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.terms_privacy_text_37), color = SoftGray, fontSize = AppTextSize.caption, lineHeight = AppTextLineHeight.body)
                        }
                    }
                }
            } else {
                // PRIVACY CONTENT
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Zero Knowledge Hero Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryEmeraldContainer.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text(stringResource(R.string.terms_privacy_zero_knowledge_architecture), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Black)
                                    Text(stringResource(R.string.terms_privacy_text_19), color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(
                                stringResource(R.string.terms_privacy_text_20),
                                color = SoftGray,
                                fontSize = AppTextSize.caption,
                                lineHeight = AppTextLineHeight.body
                            )

                            // 2x2 Grid details
                            AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                                    border = BorderStroke(0.5.dp, BorderColor)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(stringResource(R.string.terms_privacy_text_21), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(stringResource(R.string.terms_privacy_text_22), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section, fontFamily = FontFamily.Monospace)
                                        Text(stringResource(R.string.terms_privacy_text_23), color = SoftGray, fontSize = AppTextSize.dense)
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                                    border = BorderStroke(0.5.dp, BorderColor)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(stringResource(R.string.terms_privacy_text_24), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(stringResource(R.string.terms_privacy_aes_256), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section, fontFamily = FontFamily.Monospace)
                                        Text(stringResource(R.string.terms_privacy_text_25), color = SoftGray, fontSize = AppTextSize.dense)
                                    }
                                }
                            }
                        }
                    }

                    // Security Core details list
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.terms_privacy_text_26), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.terms_privacy_text_27),
                                color = SoftGray,
                                fontSize = AppTextSize.caption,
                                lineHeight = AppTextLineHeight.body
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.terms_privacy_text_28), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.terms_privacy_text_29),
                                color = SoftGray,
                                fontSize = AppTextSize.caption,
                                lineHeight = AppTextLineHeight.body
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.terms_privacy_text_38), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.terms_privacy_text_39), color = SoftGray, fontSize = AppTextSize.caption, lineHeight = AppTextLineHeight.body)
                            HorizontalDivider(color = BorderColor)
                            Text(stringResource(R.string.terms_privacy_text_40), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.terms_privacy_text_41), color = SoftGray, fontSize = AppTextSize.caption, lineHeight = AppTextLineHeight.body)
                            HorizontalDivider(color = BorderColor)
                            Text(stringResource(R.string.terms_privacy_text_42), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.terms_privacy_text_43), color = SoftGray, fontSize = AppTextSize.caption, lineHeight = AppTextLineHeight.body)
                            HorizontalDivider(color = BorderColor)
                            Text(stringResource(R.string.terms_privacy_text_44), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.terms_privacy_text_45), color = SoftGray, fontSize = AppTextSize.caption, lineHeight = AppTextLineHeight.body)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
