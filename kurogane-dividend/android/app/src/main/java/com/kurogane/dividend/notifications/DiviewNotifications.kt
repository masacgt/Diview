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
            Triple(PRICE_CHANNEL, context.getString(R.string.settings_text_11), context.getString(R.string.diview_notifications_message_2)),
            Triple(DIVIDEND_CHANNEL, context.getString(R.string.diview_notifications_message_3), context.getString(R.string.diview_notifications_message_4)),
            Triple(DIVIDEND_CHANGE_CHANNEL, context.getString(R.string.notification_dividend_change_title), context.getString(R.string.notification_dividend_change_description)),
            Triple(TEST_CHANNEL, context.getString(R.string.settings_label_32), context.getString(R.string.diview_notifications_message_6))
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

    fun post(
        id: Int,
        channel: String,
        title: String,
        text: String,
        destination: String,
        recordInHistory: Boolean = true
    ): Boolean {
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
            .setContentTitle("Diview").setContentText(context.getString(R.string.diview_notifications_message_7)).build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title.take(160)).setContentText(text.take(1000))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(1000)))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        return try {
            NotificationManagerCompat.from(context).notify(id, notification)
            if (recordInHistory) {
                historyKind(channel)?.let { kind ->
                    runCatching { NotificationStore(context).recordNotification(kind, title, text) }
                }
            }
            true
        } catch (_: SecurityException) { false }
    }

    fun test(kind: NotificationKind): Boolean {
        val (id, titleId, textId, destination) = when (kind) {
            NotificationKind.PRICE -> TestNotification(PRICE_TEST_ID, R.string.notification_test_price_title, R.string.notification_test_price_text, "watchlist")
            NotificationKind.DIVIDEND -> TestNotification(DIVIDEND_TEST_ID, R.string.notification_test_dividend_title, R.string.notification_test_dividend_text, "annual")
            NotificationKind.DIVIDEND_CHANGE -> TestNotification(DIVIDEND_CHANGE_TEST_ID, R.string.notification_test_dividend_change_title, R.string.notification_test_dividend_change_text, "annual")
        }
        manager.cancel(id)
        return post(id, channel(kind), context.getString(titleId), context.getString(textId), destination, recordInHistory = false)
    }

    fun test(): Boolean = post(TEST_ID, TEST_CHANNEL, context.getString(R.string.diview_notifications_message_8), context.getString(R.string.diview_notifications_message_9), "settings")
    fun cancel(kind: NotificationKind) = manager.cancel(when (kind) {
        NotificationKind.PRICE -> PRICE_ID
        NotificationKind.DIVIDEND -> DIVIDEND_ID
        NotificationKind.DIVIDEND_CHANGE -> DIVIDEND_CHANGE_ID
    })
    fun clear() {
        manager.cancel(PRICE_ID)
        manager.cancel(DIVIDEND_ID)
        manager.cancel(DIVIDEND_CHANGE_ID)
        manager.cancel(TEST_ID)
        manager.cancel(PRICE_TEST_ID)
        manager.cancel(DIVIDEND_TEST_ID)
        manager.cancel(DIVIDEND_CHANGE_TEST_ID)
    }

    companion object {
        const val PRICE_CHANNEL = "diview_price_alerts"
        const val DIVIDEND_CHANNEL = "diview_dividend_reminders"
        const val DIVIDEND_CHANGE_CHANNEL = "diview_dividend_changes"
        const val TEST_CHANNEL = "diview_notification_test"
        const val PRICE_ID = 8101
        const val DIVIDEND_ID = 8102
        const val DIVIDEND_CHANGE_ID = 8104
        const val TEST_ID = 8103
        const val PRICE_TEST_ID = 8105
        const val DIVIDEND_TEST_ID = 8106
        const val DIVIDEND_CHANGE_TEST_ID = 8107
        const val ACTION_OPEN = "com.kurogane.dividend.OPEN_NOTIFICATION"
        private val destinations = setOf("watchlist", "annual", "settings")

        private data class TestNotification(
            val id: Int,
            val titleId: Int,
            val textId: Int,
            val destination: String
        )

        fun channel(kind: NotificationKind): String = when (kind) {
            NotificationKind.PRICE -> PRICE_CHANNEL
            NotificationKind.DIVIDEND -> DIVIDEND_CHANNEL
            NotificationKind.DIVIDEND_CHANGE -> DIVIDEND_CHANGE_CHANNEL
        }

        private fun historyKind(channel: String): NotificationKind? = when (channel) {
            PRICE_CHANNEL -> NotificationKind.PRICE
            DIVIDEND_CHANNEL -> NotificationKind.DIVIDEND
            DIVIDEND_CHANGE_CHANNEL -> NotificationKind.DIVIDEND_CHANGE
            else -> null
        }

        fun destination(intent: Intent?): String? {
            if (intent?.action != ACTION_OPEN) return null
            val uri = intent.data ?: return null
            if (uri.scheme != "diview" || uri.authority != "notification" || uri.query != null || uri.fragment != null) return null
            return uri.path?.removePrefix("/")?.takeIf { it in destinations }
        }
    }
}
