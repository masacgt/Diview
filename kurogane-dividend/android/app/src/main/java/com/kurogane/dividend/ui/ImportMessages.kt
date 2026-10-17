package com.kurogane.dividend.ui

import kotlinx.coroutines.CancellationException

/** Only fixed validation copy owned by our importers may cross into the UI. */
fun importErrorMessage(error: Throwable, fallback: String): String {
    if (error is CancellationException || error !is Exception) throw error
    val message = error.message
    return if (error is IllegalArgumentException && message != null && message in importValidationMessages) message else fallback
}

private val importValidationMessages = setOf(
    "ファイルがサイズ上限を超えています",
    "CSVは10MB以下のファイルを選択してください",
    "CSVの引用符が閉じられていません",
    "DVMバックアップが大きすぎます",
    "DVMバックアップの形式が正しくありません",
    "未対応のDVMバックアップです",
    "バックアップに対象データがありません",
    "バックアップの中身を読み取れません",
    "バックアップの保有数量が正しくありません",
    "保有数量が上限を超えています",
    "バックアップの単価が正しくありません",
    "銘柄コード・名称を確認してください",
    "銘柄区分が一致しません",
    "保有数量は0以上の整数で指定してください",
    "取得単価が正しくありません",
    "単価が正しくありません",
    "非課税数量が正しくありません"
)
