package com.kurogane.dividend.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kurogane.dividend.notifications.DiviewNotifications

data class NotificationAccess(
    val priceAllowed: Boolean,
    val dividendAllowed: Boolean,
    val request: (String) -> Unit,
    val openSettings: () -> Unit
)

/** Permission is requested only after an explicit opt-in, never on app launch. */
@Composable
fun rememberNotificationAccess(onAllowed: (String) -> Unit): NotificationAccess {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val notifications = remember(context) { DiviewNotifications(context).also { it.createChannels() } }
    val callback by rememberUpdatedState(onAllowed)
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var priceAllowed by remember { mutableStateOf(notifications.canPost(DiviewNotifications.PRICE_CHANNEL)) }
    var dividendAllowed by remember { mutableStateOf(notifications.canPost(DiviewNotifications.DIVIDEND_CHANNEL)) }
    val openSettings = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    val channelFor: (String) -> String = { action -> when (action.substringBefore(':')) {
        "price" -> DiviewNotifications.PRICE_CHANNEL
        "dividend" -> DiviewNotifications.DIVIDEND_CHANNEL
        else -> DiviewNotifications.TEST_CHANNEL
    } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        priceAllowed = notifications.canPost(DiviewNotifications.PRICE_CHANNEL)
        dividendAllowed = notifications.canPost(DiviewNotifications.DIVIDEND_CHANNEL)
        if (granted && action != null && notifications.canPost(channelFor(action))) callback(action)
        else Toast.makeText(context, "通知が許可されていません。端末の通知設定で変更できます。", Toast.LENGTH_LONG).show()
    }
    DisposableEffect(owner, notifications) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            priceAllowed = notifications.canPost(DiviewNotifications.PRICE_CHANNEL)
            dividendAllowed = notifications.canPost(DiviewNotifications.DIVIDEND_CHANNEL)
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return NotificationAccess(priceAllowed, dividendAllowed, { action ->
        if (pending == null) {
            if (!notifications.permissionGranted() && Build.VERSION.SDK_INT >= 33) {
                pending = action
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (!notifications.canPost(channelFor(action))) {
                Toast.makeText(context, "端末の通知設定でDiviewの通知を有効にしてください。", Toast.LENGTH_LONG).show()
                openSettings()
            } else callback(action)
        }
    }, openSettings)
}
