import React, { useState } from 'react';
import { useApp } from '../../context/AppContext';

interface FileDef {
  path: string;
  name: string;
  code: string;
}

const composeFiles: FileDef[] = [
  {
    name: 'MainActivity.kt',
    path: 'android/app/src/main/java/com/kurogane/dividend/MainActivity.kt',
    code: `package com.kurogane.dividend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kurogane.dividend.ui.screens.AssetManagementScreen
import com.kurogane.dividend.ui.screens.RoadmapScreen
import com.kurogane.dividend.ui.screens.StockDetailScreen
import com.kurogane.dividend.ui.screens.WatchlistScreen
import com.kurogane.dividend.ui.theme.BackgroundDark
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.ui.theme.PrimaryEmerald
import com.kurogane.dividend.viewmodel.DividendViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DividendViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KuroganeDividendTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BackgroundDark,
                    bottomBar = {
                        val isMainTab = currentRoute in listOf("asset", "roadmap", "watchlist")
                        if (isMainTab) {
                            NavigationBar(containerColor = BackgroundDark) {
                                NavigationBarItem(
                                    selected = currentRoute == "asset",
                                    onClick = { navController.navigate("asset") },
                                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                    label = { Text("資産管理") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "roadmap",
                                    onClick = { navController.navigate("roadmap") },
                                    icon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                    label = { Text("ロードマップ") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "watchlist",
                                    onClick = { navController.navigate("watchlist") },
                                    icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                                    label = { Text("Watchlist") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "asset",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("asset") {
                            AssetManagementScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { code -> navController.navigate("detail/$code") }
                            )
                        }
                        composable("watchlist") {
                            WatchlistScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { code -> navController.navigate("detail/$code") }
                            )
                        }
                        composable("roadmap") {
                            RoadmapScreen()
                        }
                        composable("detail/{code}") { backStackEntry ->
                            val code = backStackEntry.arguments?.getString("code") ?: "7203"
                            StockDetailScreen(
                                stockCode = code,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}`
  },
  {
    name: 'AssetManagementScreen.kt',
    path: 'android/app/src/main/java/com/kurogane/dividend/ui/screens/AssetManagementScreen.kt',
    code: `package com.kurogane.dividend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetManagementScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {}
) {
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    val totalAssets = holdings.sumOf { it.totalValuation }
    val totalCost = holdings.sumOf { it.totalCost }
    val totalGain = totalAssets - totalCost
    val totalAnnualDiv = holdings.sumOf { it.annualDividend }
    val avgYield = if (totalAssets > 0) (totalAnnualDiv / totalAssets) * 100 else 0.0

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    val displayedHoldings = holdings.filter { h ->
        val monthMatch = selectedMonth == null || h.dividendMonths.contains(selectedMonth)
        val catMatch = selectedCategory == null || h.category == selectedCategory
        monthMatch && catMatch
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = PrimaryEmerald)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("資産管理", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                            Text("Asset Management", color = OnSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Total Asset Hero Card with Privacy Toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("総資産評価額", color = OnSurfaceVariant, fontSize = 12.sp)
                                IconButton(onClick = { viewModel.togglePrivacy() }) {
                                    Icon(
                                        if (isPrivacyActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Privacy",
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                if (isPrivacyActive) "+¥ ••••••" else "+¥\${currencyFormat.format(totalGain.toLong())} (+14.7%)",
                                color = PrimaryEmerald,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = if (isPrivacyActive) "¥ ••••••••" else "¥\${currencyFormat.format(totalAssets.toLong())}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 2. 1-12 Month Interactive Bar Chart (Pivot Timeline)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("月別推移グラフ (タップで絞り込み)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().height(90.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            viewModel.monthlyDividends.forEach { col ->
                                val isSelected = selectedMonth == col.month
                                Column(
                                    modifier = Modifier.weight(1f).clickable { viewModel.selectMonth(col.month) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(col.label, fontSize = 8.sp, color = if (isSelected) PrimaryEmerald else SkyBlue)
                                    Box(
                                        modifier = Modifier
                                            .width(12.dp)
                                            .height((col.amount / 500).coerceIn(8, 60).dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (isSelected) PrimaryEmerald else PrimaryEmeraldContainer)
                                    )
                                    Text("\${col.month}", fontSize = 9.sp, color = if (isSelected) PrimaryEmerald else OnSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Holdings List with Month Ribbon Badge
            items(displayedHoldings) { holding ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToDetail(holding.code) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(holding.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text("¥\${currencyFormat.format(holding.totalValuation.toLong())}", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text("\${holding.shares}株 (@\${holding.currency}\${holding.price.toInt()}) • 利回り \${holding.yieldRate}%", color = OnSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}`
  },
  {
    name: 'DividendViewModel.kt',
    path: 'android/app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt',
    code: `package com.kurogane.dividend.viewmodel

import androidx.lifecycle.ViewModel
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.MonthlyDividend
import com.kurogane.dividend.model.WatchlistStock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DividendViewModel : ViewModel() {
    private val _isPrivacyActive = MutableStateFlow(false)
    val isPrivacyActive: StateFlow<Boolean> = _isPrivacyActive.asStateFlow()

    private val _selectedMonth = MutableStateFlow<Int?>(null)
    val selectedMonth: StateFlow<Int?> = _selectedMonth.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

    fun togglePrivacy() {
        _isPrivacyActive.update { !it }
    }

    fun selectMonth(month: Int) {
        _selectedMonth.update { if (it == month) null else month }
    }

    fun selectCategory(cat: Category?) {
        _selectedCategory.value = cat
    }
}`
  },
  {
    name: 'Models.kt',
    path: 'android/app/src/main/java/com/kurogane/dividend/model/Models.kt',
    code: `package com.kurogane.dividend.model

data class Holding(
    val id: String,
    val code: String,
    val name: String,
    val sector: String,
    val market: String,
    val category: Category,
    val price: Double,
    val currency: String = "¥",
    val shares: Int,
    val acqPrice: Double,
    val dps: Double,
    val yieldRate: Double,
    val yoc: Double,
    val nextPayoutTiming: String,
    val nextPayoutAmount: String,
    val dividendMonths: List<Int>,
    val dividendType: String,
    val accountType: String,
    val dailyChange: String,
    val isPositive: Boolean
) {
    val totalValuation: Double get() = shares * price * (if (currency == "$") 154.0 else 1.0)
    val totalCost: Double get() = shares * acqPrice * (if (currency == "$") 154.0 else 1.0)
    val gainLoss: Double get() = totalValuation - totalCost
    val annualDividend: Double get() = shares * dps * (if (currency == "$") 154.0 else 1.0)
}

enum class Category {
    JAPAN, US, FUND
}`
  },
  {
    name: 'build.gradle.kts',
    path: 'android/app/build.gradle.kts',
    code: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kurogane.dividend"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kurogane.dividend"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")
}`
  }
];

export const ComposeSourceModal: React.FC<{ isOpen: boolean; onClose: () => void }> = ({
  isOpen,
  onClose
}) => {
  const { showToast } = useApp();
  const [activeFileIndex, setActiveFileIndex] = useState(0);

  if (!isOpen) return null;

  const currentFile = composeFiles[activeFileIndex];

  const handleCopy = () => {
    navigator.clipboard?.writeText(currentFile.code);
    showToast(`${currentFile.name} のKotlinコードをコピーしました`, 'copy');
  };

  const handleCopyAll = () => {
    const allCode = composeFiles.map(f => `// ================= ${f.name} (${f.path}) =================\n${f.code}\n`).join('\n');
    navigator.clipboard?.writeText(allCode);
    showToast('すべてのJetpack Composeコードを一括コピーしました！', 'done_all');
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex flex-col justify-end p-0"
      onClick={onClose}
    >
      <div
        className="bg-[#131b2e] border-t border-slate-700/80 rounded-t-3xl p-4 w-full max-w-lg mx-auto shadow-2xl relative flex flex-col max-h-[88vh] overflow-y-auto no-scrollbar"
        onClick={e => e.stopPropagation()}
      >
        <div className="w-12 h-1 bg-slate-700 rounded-full mx-auto mb-2 shrink-0" />

        {/* Header */}
        <div className="flex items-center justify-between pb-2 border-b border-slate-800 shrink-0">
          <div className="flex items-center gap-2 min-w-0">
            <div className="w-8 h-8 rounded-lg bg-[#10b981]/20 border border-[#4edea3]/40 flex items-center justify-center text-[#4edea3] shrink-0">
              <span className="material-symbols-outlined text-[20px]">android</span>
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <h3 className="font-bold text-sm text-white">Jetpack Compose ソースコード</h3>
                <span className="px-1.5 py-0.2 rounded-full bg-[#10b981]/20 text-[#4edea3] font-bold text-[9px]">
                  Kotlin 2.0
                </span>
              </div>
              <p className="text-[10px] text-[#bbcabf]">Android Studioですぐにビルドできる完全コード</p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center text-[#bbcabf] hover:text-white rounded-full bg-[#171f33]"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        {/* File Tabs */}
        <div className="flex items-center gap-1 overflow-x-auto no-scrollbar py-1">
          {composeFiles.map((file, idx) => (
            <button
              key={file.name}
              type="button"
              onClick={() => setActiveFileIndex(idx)}
              className={`px-2.5 py-1 rounded-lg text-xs font-mono shrink-0 transition-colors ${
                activeFileIndex === idx
                  ? 'bg-[#10b981] text-[#003824] font-bold shadow'
                  : 'bg-[#171f33] text-[#bbcabf] hover:text-white'
              }`}
            >
              {file.name}
            </button>
          ))}
        </div>

        {/* File Path & Copy Toolbar */}
        <div className="flex items-center justify-between px-2 py-1.5 rounded-lg bg-[#0b1326] border border-slate-800 text-[11px]">
          <span className="font-mono text-[#bbcabf] truncate max-w-[200px]">{currentFile.path}</span>
          <div className="flex items-center gap-1.5 shrink-0">
            <button
              type="button"
              onClick={handleCopy}
              className="px-2 py-0.5 rounded bg-[#171f33] hover:bg-slate-700 text-[#4edea3] font-bold flex items-center gap-1 active:scale-95 transition-all cursor-pointer"
            >
              <span className="material-symbols-outlined text-[14px]">content_copy</span>
              <span>コピー</span>
            </button>
            <button
              type="button"
              onClick={handleCopyAll}
              className="px-2 py-0.5 rounded bg-[#10b981]/20 text-[#4edea3] font-bold flex items-center gap-1 active:scale-95 transition-all cursor-pointer"
            >
              <span className="material-symbols-outlined text-[14px]">done_all</span>
              <span>全ファイル</span>
            </button>
          </div>
        </div>

        {/* Code Content View */}
        <div className="relative rounded-xl bg-[#060e20] p-3 border border-slate-800/90 overflow-x-auto max-h-96">
          <pre className="font-mono text-[11px] text-slate-300 whitespace-pre leading-relaxed select-text">
            {currentFile.code}
          </pre>
        </div>

        {/* Info Box */}
        <div className="p-2.5 rounded-xl bg-[#171f33]/80 border border-slate-800 text-[11px] text-[#bbcabf] flex items-center gap-2">
          <span className="material-symbols-outlined text-[#4edea3] text-[18px] shrink-0">check_circle</span>
          <span>リポジトリの <code>android/</code> ディレクトリにも各Kotlinファイルが直接配置されています。</span>
        </div>

        {/* Action Button */}
        <button
          type="button"
          onClick={onClose}
          className="w-full h-10 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold"
        >
          閉じる
        </button>
      </div>
    </div>
  );
};
