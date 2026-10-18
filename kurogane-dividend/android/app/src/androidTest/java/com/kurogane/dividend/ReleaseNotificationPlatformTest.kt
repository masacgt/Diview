package com.kurogane.dividend

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.kurogane.dividend.notifications.DiviewNotifications
import com.kurogane.dividend.notifications.NotificationScheduler
import com.kurogane.dividend.notifications.NotificationSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReleaseNotificationPlatformTest {
    @Test fun repeatedSchedulingKeepsOneJobAndDisablingCancelsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val work = WorkManager.getInstance(context)
        try {
            repeat(2) { NotificationScheduler.sync(context, NotificationSettings(priceEnabled = true)) }
            for (name in listOf(NotificationScheduler.PRICE_MORNING, NotificationScheduler.PRICE_EVENING)) {
                val unfinished = work.getWorkInfosForUniqueWork(name).get(15, TimeUnit.SECONDS).filterNot { it.state.isFinished }
                assertEquals(1, unfinished.size)
            }
            NotificationScheduler.sync(context, NotificationSettings())
            for (name in listOf(NotificationScheduler.PRICE_MORNING, NotificationScheduler.PRICE_EVENING)) {
                // Queries and cancellation share WorkManager's serial executor.
                assertTrue(work.getWorkInfosForUniqueWork(name).get(15, TimeUnit.SECONDS).all { it.state.isFinished })
            }
        } finally {
            NotificationScheduler.sync(context, NotificationSettings())
        }
    }

    @Test fun realPlatformPermissionControlsPublication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = DiviewNotifications(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(DiviewNotifications.TEST_ID)
        try {
            service.createChannels()
            val expected = service.canPost(DiviewNotifications.TEST_CHANNEL)
            val argument = InstrumentationRegistry.getArguments().getString("notificationPermission")
            if (argument != null) assertEquals(argument == "granted", expected)
            assertEquals(expected, service.test())
            val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
            while (expected && manager.activeNotifications.none { it.id == DiviewNotifications.TEST_ID } &&
                android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
            assertEquals(expected, manager.activeNotifications.any { it.id == DiviewNotifications.TEST_ID })
        } finally {
            manager.cancel(DiviewNotifications.TEST_ID)
        }
    }
}
