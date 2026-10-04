const fs = require('fs');
const path = require('path');

const rootDir = path.resolve(__dirname, '..');

const fileConfigs = [
  {
    id: 'MainActivity',
    name: 'MainActivity.kt',
    relPath: 'android/app/src/main/java/com/kurogane/dividend/MainActivity.kt',
    displayPath: 'app/src/main/java/com/kurogane/dividend/MainActivity.kt',
    category: 'config',
    description: 'Jetpack Composeのエントリーポイント。Navigation Composeによる画面遷移アニメーション（slideIn/slideOut/fadeIn/fadeOut）とMaterial 3 Scaffold / NavigationBarを構成。'
  },
  {
    id: 'AssetManagementScreen',
    name: 'AssetManagementScreen.kt',
    relPath: 'android/app/src/main/java/com/kurogane/dividend/ui/screens/AssetManagementScreen.kt',
    displayPath: 'app/src/main/java/com/kurogane/dividend/ui/screens/AssetManagementScreen.kt',
    category: 'screen',
    description: '資産管理ダッシュボード。総資産評価額、含み損益、プライバシーマスク機能、動的月別配当バーチャート、配当目標進捗バー、保有銘柄一覧カード、セクター絞り込み、並べ替えを完全実装。'
  },
  {
    id: 'AnnualDividendScreen',
    name: 'AnnualDividendScreen.kt',
    relPath: 'android/app/src/main/java/com/kurogane/dividend/ui/screens/AnnualDividendScreen.kt',
    displayPath: 'app/src/main/java/com/kurogane/dividend/ui/screens/AnnualDividendScreen.kt',
    category: 'screen',
    description: '年間配当金分析画面。構成銘柄/配当比率のインタラクティブ切替スイッチ、Material 3 ドーナツチャート、セクター別スタック比率、月別/年別配当推移、複利積立シミュレーターを実装。'
  },
  {
    id: 'StockDetailScreen',
    name: 'StockDetailScreen.kt',
    relPath: 'android/app/src/main/java/com/kurogane/dividend/ui/screens/StockDetailScreen.kt',
    displayPath: 'app/src/main/java/com/kurogane/dividend/ui/screens/StockDetailScreen.kt',
    category: 'screen',
    description: '個別銘柄詳細画面。リアルタイム株価、買い増しシミュレーター、4期配当推移バー、銘柄総合分析ステータス（健全性・適正バリュエーション・増配耐性・集中リスク詳細診断）を実装。'
  },
  {
    id: 'RoadmapScreen',
    name: 'RoadmapScreen.kt',
    relPath: 'android/app/src/main/java/com/kurogane/dividend/ui/screens/RoadmapScreen.kt',
    displayPath: 'app/src/main/java/com/kurogane/dividend/ui/screens/RoadmapScreen.kt',
    category: 'screen',
    description: 'FIRE・不労所得ロードマップQuest。LV1未達成/達成/LV2達成/LV3達成の動的トロフィーバッジ、10段階RPG風アチーブメント、必要追加投資元本算出を完全実装。'
  },
  {
    id: 'WatchlistScreen',
    name: 'WatchlistScreen.kt',
    relPath: 'android/app/src/main/java/com/kurogane/dividend/ui/screens/WatchlistScreen.kt',
    displayPath: 'app/src/main/java/com/kurogane/dividend/ui/screens/WatchlistScreen.kt',
    category: 'screen',
    description: '監視銘柄・買場目安ステッパー管理。目標利回りと現在利回りの乖離バー、権利落ち月バッジ、買場到達アラート表示、並べ替えボトムシートを実装。'
  },
  {
    id: 'SettingsScreen',
    name: 'SettingsScreen.kt',
    relPath: 'android/app/src/main/java/com/kurogane/dividend/ui/screens/SettingsScreen.kt',
    displayPath: 'app/src/main/java/com/kurogane/dividend/ui/screens/SettingsScreen.kt',
    category: 'screen',
    description: '設定・環境画面。ダーク/ライト/有機EL完全漆黒モード、5色アクセントカラー、税制設定、証券CSV連携ガイド、Zero Knowledge保証。'
  },
  {
    id: 'DividendViewModel',
    name: 'DividendViewModel.kt',
    relPath: 'android/app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt',
    displayPath: 'app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt',
    category: 'viewmodel',
    description: 'MVI/MVVMアーキテクチャのStateFlow管理。保有銘柄更新、ピボット月フィルタ、目標利回りリアクティブ計算、追加投資シミュレーションを集中管理。'
  },
  {
    id: 'Models',
    name: 'Models.kt',
    relPath: 'android/app/src/main/java/com/kurogane/dividend/model/Models.kt',
    displayPath: 'app/src/main/java/com/kurogane/dividend/model/Models.kt',
    category: 'model',
    description: 'Kotlinデータクラス群。Holding, WatchlistStock, RoadmapStep, MonthlyDividend等のドメインモデルと計算プロパティ。'
  },
  {
    id: 'ColorTheme',
    name: 'Color.kt & Theme.kt',
    relPath: 'android/app/src/main/java/com/kurogane/dividend/ui/theme/Color.kt',
    displayPath: 'app/src/main/java/com/kurogane/dividend/ui/theme/Color.kt',
    category: 'theme',
    description: 'Jetpack Compose Material 3 テーマ設定とカラーパレットトークン (PrimaryEmerald, BackgroundDark, SurfaceContainer)。'
  },
  {
    id: 'BuildGradle',
    name: 'build.gradle.kts',
    relPath: 'android/app/build.gradle.kts',
    displayPath: 'app/build.gradle.kts',
    category: 'config',
    description: 'Android Gradle ビルドスクリプト。Jetpack Compose BOM 2024.10, Material 3, Navigation Compose, Kotlin 2.0設定。'
  }
];

const items = fileConfigs.map(cfg => {
  const fullPath = path.join(rootDir, cfg.relPath);
  let code = '';
  if (fs.existsSync(fullPath)) {
    code = fs.readFileSync(fullPath, 'utf8');
  } else {
    console.warn(`File not found: ${fullPath}`);
  }
  return {
    id: cfg.id,
    name: cfg.name,
    path: cfg.displayPath,
    category: cfg.category,
    description: cfg.description,
    code: code
  };
});

const outputTs = `export interface ComposeFileItem {
  id: string;
  name: string;
  path: string;
  category: 'screen' | 'viewmodel' | 'model' | 'theme' | 'config' | 'dialog';
  description: string;
  code: string;
}

export const composeFiles: ComposeFileItem[] = ${JSON.stringify(items, null, 2)};
`;

const targetFile = path.join(rootDir, 'src/data/composeCodeFiles.ts');
fs.writeFileSync(targetFile, outputTs, 'utf8');
console.log(`Successfully generated ${targetFile} with ${items.length} full Compose files!`);
