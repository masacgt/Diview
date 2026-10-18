package com.kurogane.dividend.ui

import kotlinx.coroutines.CancellationException

/** Only fixed validation copy owned by our importers may cross into the UI. */
fun importErrorMessage(error: Throwable, fallback: String): String {
    if (error is CancellationException || error !is Exception) throw error
    val message = error.message
    return if (error is IllegalArgumentException && message != null) importValidationMessages[message] ?: fallback else fallback
}

private val importValidationMessages = mapOf(
    "ファイルがサイズ上限を超えています" to "ファイルがサイズ上限を超えています",
    "CSVは10MB以下のファイルを選択してください" to "CSVは10MB以下のファイルを選択してください",
    "CSVの引用符が閉じられていません" to "CSVの引用符が閉じられていません",
    "DVMバックアップが大きすぎます" to "バックアップファイルが大きすぎます",
    "DVMバックアップの形式が正しくありません" to "バックアップファイルを読み取れません",
    "未対応のDVMバックアップです" to "対応していないバックアップ形式です",
    "バックアップに対象データがありません" to "バックアップに対象データがありません",
    "バックアップの中身を読み取れません" to "バックアップの中身を読み取れません",
    "バックアップの保有数量が正しくありません" to "バックアップの保有数量が正しくありません",
    "保有数量が上限を超えています" to "保有数量が上限を超えています",
    "バックアップの単価が正しくありません" to "バックアップの単価が正しくありません",
    "銘柄コード・名称を確認してください" to "銘柄コード・名称を確認してください",
    "銘柄区分が一致しません" to "銘柄区分が一致しません",
    "保有数量は0以上の整数で指定してください" to "保有数量は0以上の整数で指定してください",
    "取得単価が正しくありません" to "取得単価が正しくありません",
    "単価が正しくありません" to "単価が正しくありません",
    "非課税数量が正しくありません" to "非課税数量が正しくありません"
)
