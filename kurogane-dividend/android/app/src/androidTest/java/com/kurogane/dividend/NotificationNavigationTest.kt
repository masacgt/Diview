package com.kurogane.dividend

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kurogane.dividend.notifications.DiviewNotifications
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationNavigationTest {
    @get:Rule(order = 0) val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private var launchedActivity: MainActivity? = null
    private var launchIntent: Intent? = null

    @After fun clean() {
        manager.cancel(DiviewNotifications.TEST_ID)
        // ActivityScenario identifies lifecycle events by the original launch Intent.
        // onNewIntent legitimately replaces it when the PendingIntent is delivered.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            launchIntent?.let { launchedActivity?.intent = it }
        }
    }

    @Test fun testNotificationProtectsLockscreenDetailsAndOpensSettings() {
        launchedActivity = compose.activity
        launchIntent = compose.activity.intent
        assertTrue(DiviewNotifications(context).test())
        compose.waitUntil(10_000) { manager.activeNotifications.any { it.id == DiviewNotifications.TEST_ID } }
        val notification = manager.activeNotifications.single { it.id == DiviewNotifications.TEST_ID }.notification
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.publicVersion)
        assertNotNull(notification.smallIcon)
        if (Build.VERSION.SDK_INT >= 31) assertTrue(notification.contentIntent.isImmutable)
        notification.contentIntent.send()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("データ管理＆バックアップ").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("データ管理＆バックアップ").assertIsDisplayed()
    }
}
