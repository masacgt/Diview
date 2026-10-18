# 配当管理 APK 16.5 内蔵サンプルCSV

これらのファイルは、利用者が指定したAPKの `assets/sampleCsv/` から抽出したもの。ファイル名と内容を変更せず、元の文字コードを維持している。

- APK: `配当管理.apk のコピー`（version 16.5 / versionCode 228）
- APK SHA-256: `FB8D3FD3F944E970380A85860D1031A7491458BF1EDD2F2C6D87ADDAFA81C3DF`
- APK内の場所: `assets/sampleCsv/`
- 保存先: `sampleCsv/`

| ファイル | サイズ | 文字コードの判定 |
|---|---:|---|
| `New_file_nomura.csv` | 3,357 bytes | UTF-8 BOM |
| `SBI_csv_data.csv` | 4,980 bytes | CP932 |
| `SaveFile (20)-1.csv` | 3,525 bytes | CP932 |
| `assetbalance(JP)_20260816_060652-1.csv` | 2,954 bytes | UTF-8（BOMなし） |
| `assetbalance(JP)_20260816_063247-1.csv` | 3,429 bytes | UTF-8（BOMなし） |
| `assetbalance(all)_20250809_111920.csv` | 2,617 bytes | CP932 |
| `assetbalance(all)_20250815_034223.csv` | 2,923 bytes | CP932 |
| `assetbalance(all)_20250825_001709_phone.csv` | 3,490 bytes | UTF-8（BOMなし） |
| `assetbalance(all)_20260819_012551.csv` | 12,854 bytes | CP932 |
| `spotbalance_20260127.csv` | 653 bytes | UTF-8（BOMなし） |
| `stockposition_20250816145033.csv` | 272 bytes | CP932 |
| `stockposition_20260226181614.csv` | 5,658 bytes | CP932 |
| `us_spotbalance_20260128.csv` | 637 bytes | CP932 |

文字コードはBOMを確認したうえでUTF-8、CP932等として厳密に読み、成功した形式を記録したもの。サンプルは仕様確認用であり、実口座で利用するCSVと同一の列・データであることまでは保証しない。
