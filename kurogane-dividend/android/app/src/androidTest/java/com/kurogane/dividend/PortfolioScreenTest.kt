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

    @Test fun repeatedRegistrationTapStartsOnlyOneRequest() {
        var requests = 0
        val pending = CompletableDeferred<Boolean>()
        compose.setContent { KuroganeDividendTheme {
            AddHoldingSheet(true, "8316", {}, { null }, { _, _ -> requests++; pending.await() })
        } }
        compose.onNodeWithText("すべての口座にまとめて登録する").performScrollTo().performClick()
        compose.onNodeWithText("銘柄情報を確認中…").assertIsDisplayed()
        compose.onNodeWithText("銘柄情報を確認中…").performClick()
        compose.runOnIdle { assertEquals(1, requests); pending.complete(false) }
    }
}
