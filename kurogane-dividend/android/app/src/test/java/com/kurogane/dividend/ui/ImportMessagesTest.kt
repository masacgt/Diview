package com.kurogane.dividend.ui

import com.kurogane.dividend.data.BrokerCsvImporter
import com.kurogane.dividend.data.CsvBroker
import com.kurogane.dividend.data.CsvTokenizer
import com.kurogane.dividend.data.readBoundedBytes
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.crypto.AEADBadTagException

class ImportMessagesTest {
    private val fallback = "バックアップを解析できませんでした"

    @Test fun doesNotExposeProviderPathsJsonContentOrCryptographicDetails() {
        for (error in listOf(
            IOException("content://provider/private/document"),
            JSONException("Unexpected private document content"),
            AEADBadTagException("Tag mismatch in internal cipher"),
            IllegalArgumentException("Internal parser details"),
            IOException()
        )) assertEquals(fallback, importErrorMessage(error, fallback))
    }

    @Test fun preservesFixedValidationCopyFromActualCsvAndBoundedReaderFailures() {
        val quoteFailure = assertThrows(IllegalArgumentException::class.java) { CsvTokenizer.parse("\"unfinished") }
        assertEquals("CSVの引用符が閉じられていません", importErrorMessage(quoteFailure, fallback))
        val sizeFailure = assertThrows(IllegalArgumentException::class.java) {
            BrokerCsvImporter.parse(ByteArray(10 * 1024 * 1024 + 1), CsvBroker.SBI)
        }
        assertEquals("CSVは10MB以下のファイルを選択してください", importErrorMessage(sizeFailure, fallback))
        val streamFailure = assertThrows(IllegalArgumentException::class.java) {
            ByteArrayInputStream(byteArrayOf(1, 2)).readBoundedBytes(1)
        }
        assertEquals("ファイルがサイズ上限を超えています", importErrorMessage(streamFailure, fallback))
    }

    @Test fun acceptsOnlyAnExactValidationMessageFromAnArgumentError() {
        assertEquals(fallback, importErrorMessage(IOException("非課税数量が正しくありません"), fallback))
        assertEquals(fallback, importErrorMessage(IllegalArgumentException("非課税数量が正しくありません: internal"), fallback))
        assertEquals("非課税数量が正しくありません", importErrorMessage(IllegalArgumentException("非課税数量が正しくありません"), fallback))
    }

    @Test fun cancellationAndVmErrorsRemainVisibleToTheCaller() {
        assertThrows(CancellationException::class.java) { importErrorMessage(CancellationException(), fallback) }
        assertThrows(OutOfMemoryError::class.java) { importErrorMessage(OutOfMemoryError(), fallback) }
    }
}
