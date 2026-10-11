package com.kurogane.dividend

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetTabNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val tabs = listOf(
        "asset" to R.string.main_activity_asset_management,
        "annual" to R.string.main_activity_annual_dividend,
        "roadmap" to R.string.main_activity_label,
        "watchlist" to R.string.main_activity_watchlist_2
    )

    private fun tab(index: Int) = compose.onNodeWithTag("main_tab_${tabs[index].first}")

    private fun openSettings() {
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.main_activity_a11y_3)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_title)).assertIsDisplayed()
    }

    private fun assertTabScreen(index: Int) {
        tab(index).assertIsSelected()
        compose.onNodeWithText(compose.activity.getString(tabs[index].second)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_title)).assertDoesNotExist()
    }

    @Test fun assetSettingsRoadmapAssetReturnsToAssetScreen() {
        compose.onNodeWithTag("asset_management_list").performScrollToKey("holdings_search")
        compose.onNode(hasSetTextAction()).performTextInput("設定経由のタブ切替")
        openSettings()
        tab(2).performClick()
        assertTabScreen(2)
        tab(0).performClick()
        assertTabScreen(0)
        compose.onNodeWithTag("asset_management_list").assertIsDisplayed()
        compose.onNodeWithTag("asset_management_list").performScrollToKey("holdings_search")
        compose.onNode(hasSetTextAction()).assertTextEquals("設定経由のタブ切替")
    }

    @Test fun everyTabOpensItsOwnScreenAfterSettingsAndAnotherTab() {
        tabs.indices.forEach { origin ->
            tabs.indices.forEach { target ->
                tab(origin).performClick()
                openSettings()
                val intermediate = (origin + 1) % tabs.size
                tab(intermediate).performClick()
                assertTabScreen(intermediate)
                tab(target).performClick()
                assertTabScreen(target)
            }
        }
    }

    @Test fun settingsBackReturnsToTheTabThatOpenedIt() {
        tabs.indices.forEach { origin ->
            tab(origin).performClick()
            openSettings()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
            assertTabScreen(origin)
        }
    }

    @Test fun repeatedTabSwitchesKeepSearchAndDoNotStackAssetScreens() {
        compose.onNodeWithTag("asset_management_list").performScrollToKey("holdings_search")
        compose.onNode(hasSetTextAction()).performTextInput("タブ切替検証")
        repeat(4) {
            compose.onNodeWithText("分析").performClick()
            compose.onNodeWithText("資産管理").performClick()
            compose.onNodeWithTag("asset_management_list").performScrollToKey("holdings_search")
            compose.onNode(hasSetTextAction()).assertTextEquals("タブ切替検証")
        }
        // Back from the root tab should finish, rather than revisit duplicate tabs.
        val activity = compose.activity
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { activity.lifecycle.currentState == Lifecycle.State.DESTROYED }
    }
}
