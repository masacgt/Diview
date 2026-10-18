# Diview CSV import and encrypted backup specification

Status: implementation-level specification for the Android code as of 2026-10-18. The JDK 17 debug build and settings/import-method UI were checked on SCG01 and an emulator; Google OAuth and live Drive upload/restore were not exercised.

## Supported brokerage CSV import

The user selects a CSV with Android's document picker, selects one of the supported broker labels when automatic detection is unavailable, reviews parsed rows, selects import fields and rows, then runs the import. CSV data is parsed on-device and is not uploaded as part of import.

| Broker label | Product types represented by the researched APK formats |
|---|---|
| SBI証券 | Japanese stocks and investment trusts; US stock text paste is a separate APK feature and is not part of CSV import |
| 楽天証券 | Japanese stocks, US stocks, and investment trusts |
| マネックス証券 | Japanese stocks, US stocks, and investment trusts |
| 松井証券 | Japanese and US stocks |
| 三菱UFJ eスマート証券 | Japanese stocks, US stocks, and investment trusts |
| 野村證券 | Japanese stocks and investment trusts |

The app reads UTF-8 (with or without BOM), CP932, and EUC-JP, with a 10 MiB file-size limit. Header matching is by column name rather than fixed position. The parser scans repeated tables and headers in one file, including SBI's account-section tables. It reads code/ticker, name, quantity, average acquisition price, current price, account, product type, market, and currency where those columns exist. Rows explicitly identified as cash, deposits, or bonds are skipped. Unsupported or ambiguous files require a manual broker selection. The app does not log in to brokerage accounts.

Fund names are matched against the local fund catalog. Unmatched funds are shown separately and are not imported. Imported rows are grouped by normalized code and merged into local holdings; existing holdings remain unless a selected field is updated. The user can choose whether shares, acquisition price, account, and current price are applied. Import writes the resulting holdings to the app's local encrypted store.

CSV samples extracted from the reference APK are retained under [samples/haitou-apk-v16.5](samples/haitou-apk-v16.5/README.md). Their original names and byte encodings are preserved.

## CSV export

The settings screen exports `Diview-portfolio.csv` through Android's Create Document picker. The user chooses a device destination. The UTF-8 CSV is not encrypted and contains one row per holding account allocation, with code, name, shares, average acquisition price, current price, and account. A leading UTF-8 BOM is included. The CSV can be selected through the same import flow.

## Encrypted backup export and Google Drive import

The export action requests a Google account authorization and saves two encrypted files in the account's Drive root. The stock file uses the reference APK's compatible name and top-level stock-array shape. The additional Diview file carries data that has no field in the legacy stock backup.

| File | Purpose |
|---|---|
| `BackUpStockInfoAndroid.dvm` | Holdings and account allocation, in the legacy-compatible stock JSON-array payload |
| `DiviewPortableData.dvm` | Diview shareholder-benefit entries, stock-climate overrides, and dividend-history overrides |

Both files use the reference APK's exact encryption procedure: AES-256-GCM with `AES/GCM/NoPadding`, a 32-byte fixed ASCII compatibility key, a fresh 12-byte `SecureRandom` nonce, and a 128-bit authentication tag. File bytes are `DVMV1` (ASCII) + nonce + ciphertext and tag; the plaintext is UTF-8 JSON. The fixed key is embedded in the APK, so this format provides compatibility and integrity but not strong secrecy from someone who can inspect the application package. Google OAuth credentials and the Drive access token are not stored in the backup file.

Drive export requests the `drive.file` scope and creates or replaces Diview's own backup files. Drive import uses Android's document picker so the user can select an existing `.dvm` in Google Drive, including files that were not created by Diview. The selected URI is read through the document provider; the app does not list all Drive files or request broad Drive access. The Google Cloud project must have the Drive API enabled and an OAuth Android client registered for package `com.kurogane.dividend` and the signing certificate SHA-1 for export. Those account/project settings are external to this repository; see Google's [Drive authorization scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth) and [Android document access](https://developer.android.com/training/data-storage/shared/documents-files). Only stock holdings and Diview extension data are applied to the current portfolio. Local backup-folder selection is not offered.

The settings screen offers two import actions: **CSVからインポート** opens the broker CSV flow, and **Googleドライブからインポート** opens Android's document picker for selecting Drive backup files. The CSV flow also offers a two-option selector (**CSVから** / **Googleドライブから**). The settings import and export segments share the same fixed-height 220 dp content viewport and action-card layout.

### Save and restore steps

1. Open **設定 → データ管理＆バックアップ → エクスポート（保存）**.
2. Tap **暗号化バックアップ（Googleドライブに保存）**.
3. On first use, select a Google account and approve the requested Drive file access. The app uploads or updates the two DVM files in that account's Drive root and displays a completion message.
4. To import, open **設定 → データ管理＆バックアップ → インポート（取込）**, tap **Googleドライブからインポート**, browse to the backup in Google Drive, and select one or more `.dvm` files.
5. The app reads the selected files, authenticates and decrypts each file, identifies supported legacy filenames or stock/Diview payload structure, then applies valid holdings and included portable edit data.

## Google Drive backup import behavior

The importer checks the backup magic/version, decrypts and authenticates the payload, parses its JSON, and applies valid stock rows. An invalid header, truncated file, wrong key, modified ciphertext, malformed JSON, or unsupported file is rejected with an error state. Holdings are merged by normalized code; import preserves existing shareholder-benefit, stock-climate, and dividend-history entries for codes not present in the imported backup and updates entries supplied by the backup.

Diview's local store separately encrypts holdings, imported backup payloads, and portable edit data with an Android Keystore AES-GCM key. Contact-feedback drafts remain device-local and are excluded from portable backup.

## Settings card sizing

The import/export segmented selector remains outside a fixed 220 dp content viewport. Both selections use the same viewport and scroll vertically inside it, so the “データ管理＆バックアップ” card keeps the same height when the selected segment changes.
