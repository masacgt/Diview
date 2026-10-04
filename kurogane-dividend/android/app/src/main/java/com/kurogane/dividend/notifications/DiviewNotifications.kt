package com.kurogane.dividend.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kurogane.dividend.MainActivity
import com.kurogane.dividend.R

class DiviewNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        listOf(
            Triple(PRICE_CHANNEL, "買場シグナル・株価アラート", "取得株価から目標利回りへの到達をお知らせします"),
            Triple(DIVIDEND_CHANNEL, "配当月リマインダー", "登録した配当受取月をお知らせします"),
            Triple(TEST_CHANNEL, "テスト通知", "通知が受信できるか確認します")
        ).forEach { (id, name, description) ->
            manager.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                this.description = description
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            })
        }
    }

    fun permissionGranted(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canPost(channel: String): Boolean = permissionGranted() && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE

    fun post(id: Int, channel: String, title: String, text: String, destination: String): Boolean {
        require(destination in destinations)
        createChannels()
        if (!canPost(channel)) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN
            data = Uri.parse("diview://notification/$destination")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Diview").setContentText("新しいお知らせがあります").build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title.take(160)).setContentText(text.take(1000))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(1000)))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        return try {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (_: SecurityException) { false }
    }

    fun test(): Boolean = post(TEST_ID, TEST_CHANNEL, "Diviewのテスト通知", "通知が有効です。タップすると通知設定を開きます。", "settings")
    fun cancel(kind: NotificationKind) = manager.cancel(if (kind == NotificationKind.PRICE) PRICE_ID else DIVIDEND_ID)
    fun clear() { manager.cancel(PRICE_ID); manager.cancel(DIVIDEND_ID); manager.cancel(TEST_ID) }

    companion object {
        const val PRICE_CHANNEL = "diview_price_alerts"
        const val DIVIDEND_CHANNEL = "diview_dividend_reminders"
        const val TEST_CHANNEL = "diview_notification_test"
        const val PRICE_ID = 8101
        const val DIVIDEND_ID = 8102
        const val TEST_ID = 8103
        const val ACTION_OPEN = "com.kurogane.dividend.OPEN_NOTIFICATION"
        private val destinations = setOf("watchlist", "annual", "settings")

        fun channel(kind: NotificationKind): String = if (kind == NotificationKind.PRICE) PRICE_CHANNEL else DIVIDEND_CHANNEL

        fun destination(intent: Intent?): String? {
            if (intent?.action != ACTION_OPEN) return null
            val uri = intent.data ?: return null
            if (uri.scheme != "diview" || uri.authority != "notification" || uri.query != null || uri.fragment != null) return null
            return uri.path?.removePrefix("/")?.takeIf { it in destinations }
        }
    }
}
