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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.kurogane.dividend.data.RoadmapImageStore
import com.kurogane.dividend.data.runCatchingCancellable
import com.kurogane.dividend.model.RoadmapAchievement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoadmapShareSheet(viewModel: DividendViewModel, achievement: RoadmapAchievement, saveImage: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = context.resources
    val currencyFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val savedTab by viewModel.roadmapShareTab.collectAsStateWithLifecycle()
    val selectedShareTab = if (achievement.canCertify) savedTab else 0
    val isAmountMasked by viewModel.roadmapShareMasked.collectAsStateWithLifecycle()
    val graphicsLayer = rememberGraphicsLayer()
    val captureKey = Triple(achievement, selectedShareTab, isAmountMasked)
    var recordedKey by remember { mutableStateOf<Triple<RoadmapAchievement, Int, Boolean>?>(null) }
    val scope = rememberCoroutineScope()
    val store = remember(context) { RoadmapImageStore(context) }
    var preparing by remember { mutableStateOf(false) }
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }
    val isBusy = preparing || pendingName != null
    BackHandler(enabled = isBusy) {}
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val name = pendingName
        if (name != null) {
            preparing = true
            scope.launch(Dispatchers.Main.immediate) {
                try {
                    val result = runCatchingCancellable { withContext(Dispatchers.IO) {
                        val file = store.pendingFile(name)
                        try {
                            if (uri != null) store.writeDocument(file, uri)
                        } catch (error: Throwable) {
                            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri!!) }
                            throw error
                        } finally { file.delete() }
                    } }
                    if (uri != null) Toast.makeText(context, if (result.isSuccess) R.string.roadmap_saved_document else R.string.roadmap_export_failed, Toast.LENGTH_LONG).show()
                } finally { pendingName = null; preparing = false }
            }
        }
    }
    fun exportImage() {
        if (preparing || pendingName != null || recordedKey != captureKey) return
        // A switch and the export action can arrive before recomposition records the new card.
        if (isAmountMasked != viewModel.roadmapShareMasked.value || savedTab != viewModel.roadmapShareTab.value) return
        preparing = true
        scope.launch(Dispatchers.Main.immediate) {
            var generated: java.io.File? = null
            var keepFile = false
            try {
                val result = runCatchingCancellable {
                    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                    val file = withContext(Dispatchers.IO) { store.createPng(bitmap) }
                    generated = file
                    when {
                        !saveImage -> {
                            context.startActivity(Intent.createChooser(store.shareIntent(file), resources.getString(R.string.roadmap_share_choose)))
                            keepFile = true // The selected app may read the content URI asynchronously.
                        }
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                            withContext(Dispatchers.IO) { store.saveToPictures(file) }
                            Toast.makeText(context, R.string.roadmap_saved_pictures, Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            pendingName = file.name
                            documentPicker.launch(file.name)
                            keepFile = true
                        }
                    }
                }
                if (result.isFailure) {
                    pendingName = null
                    Toast.makeText(context, R.string.roadmap_export_failed, Toast.LENGTH_LONG).show()
                }
            } finally {
                if (!keepFile) generated?.delete()
                preparing = false
            }
        }
    }
    run {
        ModalBottomSheet(
            onDismissRequest = { if (!preparing && pendingName == null) onDismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !preparing && pendingName == null }),
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
                // Share Modal Header matching image perfectly
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(if (saveImage) R.string.roadmap_text_15 else R.string.roadmap_text_41),
                                color = OnSurface,
                                fontSize = AppTextSize.body,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                stringResource(if (saveImage) R.string.roadmap_save_description else R.string.roadmap_text_42),
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
                            .clickable { if (!preparing && pendingName == null) onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                // Interactive Tab Selector: 進捗カード vs 踏破認定証
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.setRoadmapShareTab(0) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedShareTab == 0) PrimaryEmerald.copy(alpha = 0.12f) else SurfaceContainerHigh
                        ),
                        border = BorderStroke(1.dp, if (selectedShareTab == 0) PrimaryEmerald.copy(alpha = 0.55f) else BorderColor),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = OnSurface, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.roadmap_progress_tab, achievement.step), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.setRoadmapShareTab(1) },
                        enabled = !isBusy && achievement.canCertify,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedShareTab == 1) PrimaryEmerald.copy(alpha = 0.12f) else SurfaceContainerHigh
                        ),
                        border = BorderStroke(1.dp, if (selectedShareTab == 1) PrimaryEmerald.copy(alpha = 0.55f) else BorderColor),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.roadmap_certificate_tab, achievement.completedLevel.coerceAtLeast(1)), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }

                // Masking Switch Options Row
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceContainerHigh,
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.roadmap_text_45), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = isAmountMasked,
                            onCheckedChange = viewModel::setRoadmapShareMasked,
                            enabled = !isBusy,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnSurface,
                                checkedTrackColor = PrimaryEmerald
                            )
                        )
                    }
                }

                // Dynamic Certificate Card Box View matching Image perfectly
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("roadmap_export_card")
                        .drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                            recordedKey = captureKey
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerHigh)
                        .border(1.dp, if (selectedShareTab == 1) PrimaryEmerald.copy(alpha = 0.4f) else BorderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (selectedShareTab == 1) {
                            // Medal design matching image
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryEmerald.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryEmerald.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = PrimaryEmerald,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                stringResource(R.string.roadmap_level_complete, achievement.completedLevel),
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.roadmap_certificate_title, achievement.completedStageTitle),
                                color = OnSurface,
                                fontSize = AppTextSize.section,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (isAmountMasked) stringResource(R.string.roadmap_certificate_steps, (achievement.completedLevel - 1) * 10 + 1, achievement.completedLevel * 10)
                                else stringResource(R.string.roadmap_certificate_steps_amount, (achievement.completedLevel - 1) * 10 + 1, achievement.completedLevel * 10,
                                    currencyFormat.format(achievement.completedStageTarget), currencyFormat.format(achievement.completedStageTarget.toLong() * 12)),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Inner sub-card with highlight title
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BackgroundDark,
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        stringResource(R.string.roadmap_text_48),
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        achievement.completedStageBenefit,
                                        color = PrimaryEmeraldContainer,
                                        fontSize = AppTextSize.body,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.roadmap_text_50) + if (isAmountMasked) resources.getString(R.string.roadmap_label_5) else resources.getString(R.string.roadmap_label_6, (currencyFormat.format(achievement.monthlyIncome)).toString()),
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = AppNumericFontFamily
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            AdaptiveRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.roadmap_master_rank, achievement.completedLevel),
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.roadmap_roadmap_quest),
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        } else {
                            // Quest Card View (Tab 0 Selected)
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(SkyBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(SkyBlue.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = SkyBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                stringResource(if (achievement.completedSteps == 30) R.string.roadmap_all_complete else R.string.roadmap_current_active_quest),
                                color = SkyBlue,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.roadmap_quest_1_s, achievement.step) + if (isAmountMasked) resources.getString(R.string.roadmap_masked_target) else achievement.stepName.substringAfter(": "),
                                color = OnSurface,
                                fontSize = AppTextSize.section,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (achievement.completedSteps == 30) stringResource(R.string.roadmap_all_steps_complete)
                                else if (isAmountMasked) stringResource(R.string.roadmap_masked_goal) else stringResource(R.string.roadmap_text_52, currencyFormat.format(achievement.targetMonthly)),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Inner progress sub-card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BackgroundDark,
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        stringResource(R.string.roadmap_text_53),
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = PrimaryEmeraldContainer, modifier = Modifier.size(18.dp))
                                        Text(
                                            stringResource(R.string.roadmap_text_54, achievement.progressPercent),
                                            color = PrimaryEmeraldContainer,
                                            fontSize = AppTextSize.body,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.roadmap_text_55) + if (isAmountMasked) resources.getString(R.string.roadmap_label_5) else resources.getString(R.string.roadmap_label_6, (currencyFormat.format(achievement.monthlyIncome)).toString()),
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = AppNumericFontFamily
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            AdaptiveRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(if (achievement.completedSteps == 30) R.string.roadmap_master_rank else R.string.roadmap_challenger_rank, achievement.currentLevel),
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.roadmap_roadmap_quest),
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.app_name),
                            color = OnSurface,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            stringResource(R.string.main_activity_label),
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.caption
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Premium Gradient Share Action Button matching image perfectly
                Button(
                    onClick = { exportImage() },
                    enabled = !isBusy && recordedKey == captureKey,
                    modifier = Modifier.testTag("roadmap_export_action")
                        .fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OnSurface),
                    shape = RoundedCornerShape(12.dp),

                ) {
                    Text(
                        stringResource(if (isBusy) R.string.roadmap_export_busy else if (saveImage) R.string.roadmap_text_15 else R.string.roadmap_share_choose),
                        color = BackgroundDark,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

