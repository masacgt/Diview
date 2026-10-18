package com.kurogane.dividend

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.ui.screens.SettingsScreen
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ReleaseAccessibilityPlatformTest {
    @get:Rule val compose = createComposeRule()
    private val app = AuditApplication()
    private val models = ViewModelStore()
    private var platformAutomation: UiAutomation? = null
    private var originalAutomationFlags: Int? = null

    @After fun clean() {
        originalAutomationFlags?.let { flags ->
            platformAutomation?.let { automation ->
                automation.serviceInfo = automation.serviceInfo.apply { this.flags = flags }
            }
        }
        compose.runOnIdle { models.clear() }
        app.cleanUp()
    }

    private fun slider(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.rangeInfo?.min == 1f && node.rangeInfo?.max == 10f &&
            node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id }) return node
        for (index in 0 until node.childCount) slider(node.getChild(index))?.let { return it }
        return null
    }

    @Test fun platformAccessibilityFocusAndIncrementActionsKeepIntegerBounds() {
        lateinit var vm: DividendViewModel
        compose.runOnIdle {
            vm = DividendViewModel(app, false, CloudflareMarketRepository("https://example.com") {
                throw IOException()
            }, notificationScheduler = { _, _ -> })
            models.put("accessibility", vm)
        }
        runBlocking { vm.awaitRestoration() }
        compose.setContent { KuroganeDividendTheme { SettingsScreen(vm) } }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        platformAutomation = automation
        val manager = app.getSystemService(AccessibilityManager::class.java)
        if (InstrumentationRegistry.getArguments().getString("requireTalkBack") == "true") {
            assertTrue("The actual TalkBack service must stay connected",
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                    it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback"
                })
            assertTrue(manager.isTouchExplorationEnabled)
        }
        // Compose requires touch exploration before accepting accessibility focus.
        // UiAutomation does not request it by default; restore its flags in @After.
        val serviceInfo = automation.serviceInfo
        originalAutomationFlags = serviceInfo.flags
        serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        automation.serviceInfo = serviceInfo
        compose.waitUntil(10_000) { manager.isTouchExplorationEnabled }
        compose.waitUntil(10_000) { slider(automation.rootInActiveWindow) != null }
        val focusNode = slider(automation.rootInActiveWindow)!!
        assertTrue(focusNode.isAccessibilityFocused || focusNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        compose.waitUntil(10_000) { slider(automation.rootInActiveWindow)?.isAccessibilityFocused == true }
        fun readySlider(): AccessibilityNodeInfo {
            compose.waitForIdle()
            automation.waitForIdle(100, 5_000)
            return requireNotNull(slider(automation.rootInActiveWindow)).apply { refresh() }
        }
        fun setProgress(value: Float) {
            val node = readySlider()
            assertTrue("Platform setProgress $value from ${node.rangeInfo?.current}", node.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value) }))
            compose.waitUntil(10_000) { vm.compositionThreshold.value == value.toInt() }
        }
        setProgress(1f)
        assertTrue(readySlider().performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        compose.waitUntil(10_000) { vm.compositionThreshold.value == 2 }
        assertTrue(readySlider().performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
        compose.waitUntil(10_000) { vm.compositionThreshold.value == 1 }
        setProgress(10f)
        assertTrue(readySlider().performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
        compose.waitUntil(10_000) { vm.compositionThreshold.value == 9 }
    }
}
