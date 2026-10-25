package com.kurogane.dividend

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.ui.components.AddHoldingSheet
import com.kurogane.dividend.ui.components.SearchSheet
import com.kurogane.dividend.ui.screens.StockDetailScreen
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.After
import androidx.lifecycle.ViewModelStore
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import com.kurogane.dividend.data.CloudflareSearchHit
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.ui.screens.ContactFeedbackScreen
import androidx.compose.runtime.mutableStateOf
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import androidx.compose.ui.geometry.Offset

@RunWith(AndroidJUnit4::class)
class PortfolioScreenTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    @After fun clearModels() { compose.runOnIdle { models.clear() } }

    @Test fun unknownStockShowsAnErrorInsteadOfAnotherHolding() {
        val model = DividendViewModel(ReviewApplication(), false, CloudflareMarketRepository("https://example.com") { throw IOException("offline") })
        models.put("screen", model)
        compose.setContent { KuroganeDividendTheme { StockDetailScreen("UNKNOWN", model) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("銘柄情報を取得できませんでした").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("銘柄情報を取得できませんでした").assertIsDisplayed()
        compose.onAllNodesWithText("トヨタ自動車").assertCountEquals(0)
    }

    @Test fun failedSearchKeepsLocalMatchingResultsAvailable() {
        val attempts = AtomicInteger()
        compose.setContent { KuroganeDividendTheme { SearchSheet(true, {}, {}, { attempts.incrementAndGet(); throw IOException("offline") }) } }
        compose.onNode(hasSetTextAction()).performTextInput("7203")
        compose.waitUntil(10_000) { attempts.get() == 1 && compose.onAllNodesWithText("トヨタ自動車").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("トヨタ自動車").assertIsDisplayed()
    }

    @Test fun replacingAQueryCannotDisplayThePreviousResponse() {
        val oldResponse = CompletableDeferred<List<CloudflareSearchHit>>()
        val attempts = AtomicInteger()
        compose.setContent { KuroganeDividendTheme { SearchSheet(true, {}, {}, { query ->
            attempts.incrementAndGet()
            if (query == "7203") oldResponse.await() else listOf(CloudflareSearchHit("8316", "新しい結果"))
        }) } }
        compose.onNode(hasSetTextAction()).performTextInput("7203")
        compose.waitUntil(10_000) { attempts.get() == 1 }
        compose.onNode(hasSetTextAction()).performTextReplacement("8316")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("新しい結果").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { oldResponse.complete(listOf(CloudflareSearchHit("7203", "古い結果"))) }
        compose.onNodeWithText("新しい結果").assertIsDisplayed()
        compose.onAllNodesWithText("古い結果").assertCountEquals(0)
    }

    @Test fun downwardSwipeClosesSearchWithKeyboardAndAllowsReopening() {
        val visible = mutableStateOf(true)
        var dismissals = 0
        compose.setContent { KuroganeDividendTheme {
            SearchSheet(visible.value, { dismissals++; visible.value = false }, {}, { throw IOException("offline") })
        } }
        compose.onNode(hasSetTextAction()).performTextInput("7203")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("トヨタ自動車").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("銘柄検索・絞り込み").performTouchInput {
            swipe(Offset(centerX, centerY), Offset(centerX, centerY + 700f), durationMillis = 250)
        }
        compose.waitUntil(10_000) { !visible.value }
        compose.onNodeWithText("銘柄検索・絞り込み").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissals); visible.value = true }
        compose.onNode(hasSetTextAction()).performTextInput("8316")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("三井住友FG").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("三井住友FG").assertIsDisplayed()
    }

    @Test fun scrollingSearchResultsDoesNotCloseTheSheet() {
        var dismissals = 0
        compose.setContent { KuroganeDividendTheme {
            SearchSheet(true, { dismissals++ }, {}, {
                (0 until 30).map { index -> CloudflareSearchHit((3000 + index).toString(), "検索結果 $index") }
            })
        } }
        compose.onNode(hasSetTextAction()).performTextInput("7203")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("検索結果 0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("stock_search_results").performTouchInput { swipeUp(durationMillis = 300) }
        compose.onNodeWithText("銘柄検索・絞り込み").assertIsDisplayed()
        compose.onNodeWithText("検索結果 0").assertIsNotDisplayed()
        compose.runOnIdle { assertEquals(0, dismissals) }
    }

    @Test fun acquisitionPricesHaveNoGroupingAndPastedPriceCanRegister() {
        var registeredPrice: Double? = null
        compose.setContent { KuroganeDividendTheme {
            AddHoldingSheet(
                true, "8058", {},
                onSearchSecurities = { emptyList() },
                onResolveSecurity = { null },
                onRegisterCollective = { _, allocations, _ ->
                    registeredPrice = allocations.first().acquisitionPrice
                    true
                }
            )
        } }
        listOf(1, 3, 5, 7).forEach { index ->
            compose.onAllNodes(hasSetTextAction())[index].performScrollTo().assertTextEquals("3240")
        }
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextReplacement("2,015")
        compose.onAllNodes(hasSetTextAction())[1].assertTextEquals("2015")
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextReplacement("100")
        compose.onNodeWithText("すべての口座にまとめて登録する")
            .performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { registeredPrice != null }
        compose.runOnIdle { assertEquals(2015.0, registeredPrice!!, 0.0) }
    }

    @Test fun repeatedRegistrationTapStartsOnlyOneRequest() {
        var requests = 0
        val pending = CompletableDeferred<Boolean>()
        compose.setContent { KuroganeDividendTheme {
            AddHoldingSheet(
                true,
                "8316",
                {},
                onSearchSecurities = { emptyList() },
                onResolveSecurity = { null },
                onRegisterCollective = { _, _, _ -> requests++; pending.await() }
            )
        } }
        // Account quantities default to zero; enter valid data before testing duplicate taps.
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextReplacement("1000")
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextReplacement("100")
        compose.onNodeWithText("すべての口座にまとめて登録する").performScrollTo().performClick()
        compose.waitUntil(10_000) { requests == 1 }
        compose.onNodeWithText("銘柄情報を確認中…").assertIsDisplayed()
        compose.onNodeWithText("銘柄情報を確認中…").performClick()
        compose.runOnIdle { assertEquals(1, requests); pending.complete(false) }
    }

    @Test fun leavingFeedbackKeepsAnEncryptedDraftAndRestoresIt() {
        val app = ReviewApplication()
        val store = LocalImportStore(app)
        store.clearFeedbackDraft()
        val model = DividendViewModel(app, false, CloudflareMarketRepository("https://example.com") { throw IOException("offline") }, notificationScheduler = { _, _ -> })
        models.put("feedback", model)
        val visible = mutableStateOf(true)
        compose.setContent { KuroganeDividendTheme { if (visible.value) ContactFeedbackScreen({}, model) } }
        try {
            compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextInput("画面を閉じても残る日本語の下書き")
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            assertEquals("画面を閉じても残る日本語の下書き", store.loadFeedbackDraft()!!.getString("message"))
            assertFalse(app.getSharedPreferences("local_import_store", Context.MODE_PRIVATE).getString("feedback_draft", "")!!.contains("日本語の下書き"))
            compose.runOnIdle { visible.value = true }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("画面を閉じても残る日本語の下書き").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextReplacement("")
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            assertNull(store.loadFeedbackDraft())
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            store.clearFeedbackDraft()
        }
    }
}
