package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import com.kurogane.dividend.ui.components.AdaptiveTopAppBar
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.data.runCatchingCancellable
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactFeedbackScreen(
    onBack: () -> Unit,
    viewModel: DividendViewModel
) {
    var selectedCategory by rememberSaveable { mutableStateOf("feature") }
    var senderName by rememberSaveable { mutableStateOf("") }
    var senderEmail by rememberSaveable { mutableStateOf("") }
    var messageText by rememberSaveable { mutableStateOf("") }
    var attachDiagnostics by rememberSaveable { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var requestId by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var lastPayload by rememberSaveable { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val draftStore = remember(viewModel) { LocalImportStore(viewModel.getApplication<android.app.Application>()) }
    var draftMessage by remember { mutableStateOf<String?>(null) }
    var draftLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatchingCancellable { withContext(Dispatchers.IO) { draftStore.loadFeedbackDraft() } }.onSuccess { draft ->
            if (draft != null) {
                senderName = draft.optString("name")
                senderEmail = draft.optString("email")
                messageText = draft.optString("message")
                selectedCategory = draft.optString("category", "feature")
                requestId = draft.optString("requestId", java.util.UUID.randomUUID().toString())
                lastPayload = draft.optString("contentFingerprint").takeIf { it.isNotBlank() }
                attachDiagnostics = draft.optBoolean("attachDiagnostics", draft.has("diagnostics"))
            }
        }.onFailure { draftMessage = context.getString(R.string.contact_feedback_message) }
        draftLoaded = true
    }
    val currentDraft by rememberUpdatedState(JSONObject().apply {
        put("category", selectedCategory); put("name", senderName); put("email", senderEmail)
        put("message", messageText); put("requestId", requestId); put("attachDiagnostics", attachDiagnostics)
        lastPayload?.let { put("contentFingerprint", it) }
    })
    val canSaveDraft by rememberUpdatedState(draftLoaded)
    DisposableEffect(draftStore) {
        onDispose {
            // Locking the app removes the form; keep its input encrypted for the next visit.
            if (canSaveDraft) runCatching {
                if (currentDraft.optString("message").isNotBlank()) draftStore.saveFeedbackDraft(currentDraft)
                else draftStore.clearFeedbackDraft()
            }.onFailure {
                android.widget.Toast.makeText(context, context.getString(com.kurogane.dividend.R.string.draft_save_failed), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val scrollState = rememberScrollState()

    val faqs = remember {
        listOf(
            FAQItem(
                context.getString(R.string.contact_feedback_message_2),
                context.getString(R.string.contact_feedback_message_3)
            ),
            FAQItem(
                context.getString(R.string.contact_feedback_message_4),
                context.getString(R.string.contact_feedback_message_5)
            ),
            FAQItem(
                context.getString(R.string.contact_feedback_message_6),
                context.getString(R.string.contact_feedback_message_7)
            ),
            FAQItem(
                context.getString(R.string.contact_feedback_message_8),
                context.getString(R.string.contact_feedback_message_9)
            )
        )
    }

    var openFaqIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ContactSupport,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.contact_feedback_text), color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Support Desk Intro Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PrimaryEmeraldContainer.copy(alpha = 0.15f))
                            .border(1.dp, PrimaryEmeraldContainer.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HeadsetMic,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PrimaryEmeraldContainer.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(stringResource(R.string.contact_feedback_official_support), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryEmerald)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.contact_feedback_text_2), color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.contact_feedback_text_3),
                            color = SoftGray,
                            fontSize = AppTextSize.dense,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // FAQ Accordion Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.contact_feedback_text_4), color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.contact_feedback_text_5), color = SoftGray, fontSize = AppTextSize.dense)
                }

                faqs.forEachIndexed { idx, faq ->
                    val open = openFaqIndex == idx
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openFaqIndex = if (open) null else idx },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryEmeraldContainer.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(stringResource(R.string.contact_feedback_q), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Black)
                                    }
                                    Text(faq.q, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                                Icon(
                                    imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = SoftGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (open) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BackgroundDark.copy(alpha = 0.5f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        faq.a,
                                        color = SoftGray,
                                        fontSize = AppTextSize.dense,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Contact Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.RateReview, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.contact_feedback_text_6), color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }

                    // Category Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.contact_feedback_text_7), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.SemiBold)
                        val categories = listOf(
                            CategoryButton("feature", "機能の改善要望", Icons.Default.Lightbulb),
                            CategoryButton("bug", "不具合の報告", Icons.Default.BugReport),
                            CategoryButton("stock", "銘柄データの誤り", Icons.Default.TableChart),
                            CategoryButton("other", "その他", Icons.Default.MoreHoriz)
                        )

                        AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.take(2).forEach { cat ->
                                val active = selectedCategory == cat.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) PrimaryEmerald else BackgroundDark)
                                        .border(BorderStroke(1.dp, if (active) Color.Transparent else BorderColor), RoundedCornerShape(8.dp))
                                        .clickable { selectedCategory = cat.id },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(cat.icon, contentDescription = null, tint = if (active) OnPrimary else SoftGray, modifier = Modifier.size(14.dp))
                                        Text(cat.label, color = if (active) OnPrimary else SoftGray, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.drop(2).forEach { cat ->
                                val active = selectedCategory == cat.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) PrimaryEmerald else BackgroundDark)
                                        .border(BorderStroke(1.dp, if (active) Color.Transparent else BorderColor), RoundedCornerShape(8.dp))
                                        .clickable { selectedCategory = cat.id },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(cat.icon, contentDescription = null, tint = if (active) OnPrimary else SoftGray, modifier = Modifier.size(14.dp))
                                        Text(cat.label, color = if (active) OnPrimary else SoftGray, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Sender Name Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.contact_feedback_text_8), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.contact_feedback_text_9), color = SoftGray, fontSize = AppTextSize.dense)
                        }
                        OutlinedTextField(
                            value = senderName,
                            onValueChange = { senderName = it },
                            placeholder = { Text(stringResource(R.string.contact_feedback_text_10), color = SoftGray, fontSize = AppTextSize.dense) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BackgroundDark,
                                unfocusedContainerColor = BackgroundDark,
                                focusedBorderColor = PrimaryEmerald,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Return Email Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.contact_feedback_text_11), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.contact_feedback_text_12), color = SoftGray, fontSize = AppTextSize.dense)
                        }
                        OutlinedTextField(
                            value = senderEmail,
                            onValueChange = { senderEmail = it },
                            placeholder = { Text(stringResource(R.string.contact_feedback_your_email_example_com), color = SoftGray, fontSize = AppTextSize.dense) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BackgroundDark,
                                unfocusedContainerColor = BackgroundDark,
                                focusedBorderColor = PrimaryEmerald,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Message Text Multi-Line Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.contact_feedback_text_13), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text(stringResource(R.string.contact_feedback_text_14), color = SoftGray, fontSize = AppTextSize.dense) },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BackgroundDark,
                                unfocusedContainerColor = BackgroundDark,
                                focusedBorderColor = PrimaryEmerald,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Diagnostics Attachment Toggle
                    AdaptiveRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundDark)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.contact_feedback_text_15), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.contact_feedback_text_16), color = SoftGray, fontSize = AppTextSize.dense)
                        }
                        Switch(
                            checked = attachDiagnostics,
                            onCheckedChange = { attachDiagnostics = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryEmerald)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val canSubmit = draftLoaded && !isSubmitting && messageText.trim().isNotEmpty() && messageText.length <= 20_000 &&
                        senderName.length <= 200 && senderEmail.length <= 254 &&
                        (senderEmail.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(senderEmail.trim()).matches())
                    Button(
                        onClick = {
                            if (!isSubmitting && messageText.trim().isNotEmpty()) {
                                isSubmitting = true
                                val draft = JSONObject().apply {
                                    put("category", selectedCategory); put("name", senderName.trim())
                                    put("email", senderEmail.trim()); put("message", messageText.trim())
                                    if (attachDiagnostics) put("diagnostics", JSONObject().put("androidSdk", android.os.Build.VERSION.SDK_INT).put("appVersion", com.kurogane.dividend.BuildConfig.VERSION_NAME))
                                }
                                val fingerprint = draft.toString()
                                if (lastPayload != fingerprint) requestId = java.util.UUID.randomUUID().toString()
                                lastPayload = fingerprint
                                draft.put("requestId", requestId)
                                draft.put("contentFingerprint", fingerprint)
                                coroutineScope.launch {
                                    var draftSaved = false
                                    try {
                                        runCatchingCancellable {
                                            withContext(Dispatchers.IO) { draftStore.saveFeedbackDraft(draft) }
                                            draftSaved = true
                                            com.kurogane.dividend.data.FeedbackRepository().submit(JSONObject(draft.toString()).apply { remove("contentFingerprint") })
                                        }.onSuccess { receipt ->
                                            draftMessage = context.getString(R.string.contact_feedback_message_10, (receipt).toString())
                                            if (messageText.trim() == draft.optString("message")) messageText = ""
                                            runCatchingCancellable { withContext(Dispatchers.IO) { draftStore.clearFeedbackDraft() } }
                                                .onFailure { draftMessage = context.getString(com.kurogane.dividend.R.string.draft_clear_failed) }
                                        }.onFailure { error ->
                                            draftMessage = context.getString(R.string.contact_feedback_message_11) + (if (draftSaved) "内容は端末に保存されています。" else "入力内容を確認して再試行してください。") +
                                                ((error as? com.kurogane.dividend.data.ServiceException)?.message ?: "通信環境を確認して再試行してください。")
                                        }
                                    } finally { isSubmitting = false }
                                }
                            }
                        },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryEmeraldContainer,
                            contentColor = OnPrimaryContainer,
                            disabledContainerColor = SurfaceContainerHighest,
                            disabledContentColor = OnSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = OnSurfaceVariant, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = if (canSubmit) OnPrimaryContainer else OnSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text(stringResource(R.string.contact_feedback_text_17), color = if (canSubmit) OnPrimaryContainer else OnSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                            }
                        }
                    }
                    draftMessage?.let { Text(it, color = OnSurfaceVariant, fontSize = AppTextSize.caption) }
                }
            }
        }
    }
}

data class FAQItem(val q: String, val a: String)
data class CategoryButton(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
