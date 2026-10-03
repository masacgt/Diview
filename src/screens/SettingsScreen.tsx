import React, { useState } from 'react';
import { useApp } from '../context/AppContext';
import { AccentColor, ThemeMode } from '../types';

export const SettingsScreen: React.FC = () => {
  const {
    themeMode,
    setThemeMode,
    isOledBlack,
    setIsOledBlack,
    accentColor,
    setAccentColor,
    navigateTo,
    showToast
  } = useApp();

  const [dataTab, setDataTab] = useState<'import' | 'export'>('import');
  const [calcBasis, setCalcBasis] = useState<'eval' | 'cost'>('eval');
  const [taxDeduction, setTaxDeduction] = useState(true);
  const [usTaxDeduction, setUsTaxDeduction] = useState(true);
  const [targetYield, setTargetYield] = useState('4.0');
  const [threshold, setThreshold] = useState(5);
  const [notifyExDiv, setNotifyExDiv] = useState(true);
  const [notifyBuySignal, setNotifyBuySignal] = useState(true);
  const [biometrics, setBiometrics] = useState(true);
  const [maskAtStartup, setMaskAtStartup] = useState(false);

  const accentOptions: { id: AccentColor; name: string; color: string }[] = [
    { id: 'emerald', name: 'エメラルド', color: '#10b981' },
    { id: 'cyan', name: 'シアン', color: '#06b6d4' },
    { id: 'blue', name: 'ブルー', color: '#3b82f6' },
    { id: 'amber', name: 'アンバー', color: '#f59e0b' },
    { id: 'purple', name: 'パープル', color: '#8b5cf6' }
  ];

  return (
    <div className="flex flex-col w-full max-w-lg mx-auto px-3.5 pt-18 pb-28 gap-4">
      {/* 1. データ管理＆バックアップ Section */}
      <section className="space-y-2.5">
        <div className="flex items-center gap-2 px-1">
          <span className="material-symbols-outlined text-[#4edea3] text-[20px]">database</span>
          <h3 className="text-xs font-bold text-white tracking-wide uppercase">データ管理＆バックアップ</h3>
        </div>

        <div className="bg-[#131b2e] rounded-2xl p-4 border border-slate-800 space-y-3.5 shadow-md">
          {/* Dual Segment Tab */}
          <div className="p-1 bg-[#0b1326] rounded-xl flex items-center border border-slate-800">
            <button
              type="button"
              onClick={() => setDataTab('import')}
              className={`flex-1 py-1.5 rounded-lg text-xs font-bold flex items-center justify-center gap-1.5 transition-all ${
                dataTab === 'import'
                  ? 'bg-[#10b981]/20 text-[#4edea3] border border-[#4edea3]/40 shadow-sm'
                  : 'text-[#bbcabf] hover:text-white'
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">upload_file</span>
              <span>インポート (取込)</span>
            </button>
            <button
              type="button"
              onClick={() => setDataTab('export')}
              className={`flex-1 py-1.5 rounded-lg text-xs font-bold flex items-center justify-center gap-1.5 transition-all ${
                dataTab === 'export'
                  ? 'bg-[#10b981]/20 text-[#4edea3] border border-[#4edea3]/40 shadow-sm'
                  : 'text-[#bbcabf] hover:text-white'
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">download</span>
              <span>エクスポート (保存)</span>
            </button>
          </div>

          {/* Import Pane */}
          {dataTab === 'import' && (
            <div className="space-y-3">
              <div>
                <div className="flex items-center justify-between text-[11px] mb-1.5">
                  <span className="font-semibold text-[#bbcabf]">対応証券口座フォーマット</span>
                  <span className="text-[#4edea3] flex items-center gap-0.5 font-medium text-[10px]">
                    <span className="material-symbols-outlined text-[12px]">verified</span>自動判定対応
                  </span>
                </div>
                <div className="grid grid-cols-4 gap-1.5 text-center">
                  {['SBI証券', '楽天証券', 'マネックス', 'moomoo'].map(b => (
                    <div key={b} className="py-1 px-1 rounded-lg bg-[#0b1326] border border-slate-800 text-white flex flex-col items-center">
                      <span className="text-[11px] font-bold">{b}</span>
                      <span className="text-[8.5px] text-[#bbcabf]">CSV読込</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Upload Dropzone */}
              <div
                onClick={() => navigateTo('csv-import')}
                className="p-4 border-2 border-dashed border-[#4edea3]/30 hover:border-[#4edea3] rounded-xl bg-[#0b1326]/60 hover:bg-[#0b1326] cursor-pointer flex flex-col items-center justify-center text-center transition-all group"
              >
                <div className="w-10 h-10 rounded-full bg-[#10b981]/15 text-[#4edea3] flex items-center justify-center mb-1.5 group-hover:scale-110 transition-transform">
                  <span className="material-symbols-outlined text-[22px]">cloud_upload</span>
                </div>
                <span className="text-xs font-bold text-white">CSVファイルを選択 または ウィザードを開く</span>
                <span className="text-[10px] text-[#bbcabf] mt-0.5">UTF-8 / Shift-JIS自動検知 (最大 10MB)</span>
              </div>

              <div className="flex items-center justify-between text-[11px] text-[#bbcabf] pt-1">
                <span>前回インポート: 2024/10/24 (SBI証券)</span>
                <button
                  type="button"
                  onClick={() => navigateTo('csv-import')}
                  className="text-[#4edea3] hover:underline font-medium"
                >
                  ウィザードへ ➔
                </button>
              </div>
            </div>
          )}

          {/* Export Pane */}
          {dataTab === 'export' && (
            <div className="space-y-3">
              <p className="text-xs text-[#bbcabf] leading-relaxed">
                登録中の全保有株・配当情報・取得単価・為替レート設定をCSVまたはJSON形式で書き出します。
              </p>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => showToast('全保有株CSVを書き出しました (42KB)', 'download_done')}
                  className="py-2.5 px-3 rounded-xl bg-[#0b1326] hover:bg-slate-800 border border-slate-700 text-white text-xs font-semibold flex items-center justify-center gap-1.5"
                >
                  <span className="material-symbols-outlined text-[16px] text-[#4edea3]">table_view</span>
                  <span>CSV形式で保存</span>
                </button>
                <button
                  type="button"
                  onClick={() => showToast('完全復元用JSONバックアップを作成しました', 'download_done')}
                  className="py-2.5 px-3 rounded-xl bg-[#0b1326] hover:bg-slate-800 border border-slate-700 text-white text-xs font-semibold flex items-center justify-center gap-1.5"
                >
                  <span className="material-symbols-outlined text-[16px] text-indigo-300">code_blocks</span>
                  <span>JSONバックアップ</span>
                </button>
              </div>
            </div>
          )}

          <div className="pt-2 border-t border-slate-800 text-[10px] text-[#bbcabf] flex items-center gap-1">
            <span className="material-symbols-outlined text-[14px] text-[#4edea3]">lock</span>
            <span>CSVファイルは端末内でのみ安全に処理され、外部送信されることはありません。</span>
          </div>
        </div>
      </section>

      {/* 2. 外観・テーマ設定 Section */}
      <section className="space-y-2.5">
        <div className="flex items-center gap-2 px-1">
          <span className="material-symbols-outlined text-[#4edea3] text-[20px]">palette</span>
          <h3 className="text-xs font-bold text-white tracking-wide uppercase">外観・テーマ設定</h3>
        </div>

        <div className="bg-[#131b2e] rounded-2xl p-4 border border-slate-800 space-y-3.5 shadow-md">
          {/* Theme Selector */}
          <div>
            <div className="flex items-center justify-between text-xs mb-2">
              <span className="font-semibold text-white">カラーテーマ</span>
              <span className="text-[10px] text-[#4edea3] font-bold">
                {themeMode === 'dark' ? (isOledBlack ? '有機ELダーク' : 'ダーク適用中') : themeMode === 'light' ? 'ライト' : 'システム連動'}
              </span>
            </div>

            <div className="grid grid-cols-3 gap-2">
              {[
                { id: 'dark', label: 'ダーク', icon: 'dark_mode' },
                { id: 'light', label: 'ライト', icon: 'light_mode' },
                { id: 'system', label: 'システム', icon: 'devices' }
              ].map(t => (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => setThemeMode(t.id as ThemeMode)}
                  className={`py-2 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-all ${
                    themeMode === t.id
                      ? 'bg-[#10b981]/20 border border-[#4edea3] text-[#4edea3] font-bold'
                      : 'bg-[#0b1326] border border-slate-800 text-[#bbcabf] hover:text-white'
                  }`}
                >
                  <span className="material-symbols-outlined text-[16px]">{t.icon}</span>
                  <span>{t.label}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Accent Color Selection */}
          <div className="border-t border-slate-800 pt-3">
            <div className="flex items-center justify-between text-xs mb-2">
              <span className="font-semibold text-white">アクセントカラー</span>
              <span className="text-[11px] font-bold capitalize text-[#4edea3]">{accentColor}</span>
            </div>
            <div className="flex items-center justify-between px-2">
              {accentOptions.map(acc => (
                <button
                  key={acc.id}
                  type="button"
                  onClick={() => {
                    setAccentColor(acc.id);
                    showToast(`アクセントカラーを ${acc.name} に変更しました`);
                  }}
                  style={{ backgroundColor: acc.color }}
                  className={`w-9 h-9 rounded-full flex items-center justify-center cursor-pointer transition-transform hover:scale-105 active:scale-95 ${
                    accentColor === acc.id ? 'ring-2 ring-white ring-offset-2 ring-offset-[#131b2e] scale-110 shadow-lg' : 'opacity-85'
                  }`}
                >
                  {accentColor === acc.id && (
                    <span className="material-symbols-outlined text-white text-[18px] font-bold">check</span>
                  )}
                </button>
              ))}
            </div>
          </div>

          {/* OLED Black Switch */}
          <div className="border-t border-slate-800 pt-3 flex items-center justify-between">
            <div>
              <div className="flex items-center gap-1.5">
                <span className="text-xs font-semibold text-white">有機ELダーク (完全ブラック)</span>
                <span className="text-[9px] bg-[#10b981]/15 text-[#4edea3] px-1.5 py-0.2 rounded font-bold">節電</span>
              </div>
              <p className="text-[10px] text-[#bbcabf] mt-0.5">ピクセル発光を最小化し黒色消費電力を抑制</p>
            </div>
            <button
              type="button"
              onClick={() => {
                setIsOledBlack(!isOledBlack);
                showToast(isOledBlack ? '通常ダークモードに戻しました' : '有機EL完全ブラックモードを適用しました');
              }}
              className={`w-11 h-6 rounded-full p-0.5 transition-colors ${isOledBlack ? 'bg-[#10b981]' : 'bg-slate-700'}`}
            >
              <span className={`block w-5 h-5 rounded-full bg-white shadow transition-transform ${isOledBlack ? 'translate-x-5' : 'translate-x-0'}`} />
            </button>
          </div>
        </div>
      </section>

      {/* 3. 表示・計算カスタマイズ Section */}
      <section className="space-y-2.5">
        <div className="flex items-center gap-2 px-1">
          <span className="material-symbols-outlined text-[#4edea3] text-[20px]">tune</span>
          <h3 className="text-xs font-bold text-white tracking-wide uppercase">表示・計算カスタマイズ</h3>
        </div>

        <div className="bg-[#131b2e] rounded-2xl p-4 border border-slate-800 space-y-3.5 shadow-md">
          {/* Basis */}
          <div>
            <span className="text-xs font-semibold text-white block mb-1.5">配当利回りの計算基準</span>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setCalcBasis('eval')}
                className={`p-2.5 rounded-xl border text-left transition-all ${
                  calcBasis === 'eval'
                    ? 'bg-[#171f33] border-[#4edea3] text-white'
                    : 'bg-[#0b1326] border-slate-800 text-[#bbcabf]'
                }`}
              >
                <div className="flex items-center justify-between text-xs font-bold">
                  <span>評価額基準</span>
                  <span className={`material-symbols-outlined text-[16px] ${calcBasis === 'eval' ? 'text-[#4edea3]' : 'text-slate-600'}`}>
                    {calcBasis === 'eval' ? 'radio_button_checked' : 'radio_button_unchecked'}
                  </span>
                </div>
                <span className="text-[10px] text-[#bbcabf] mt-0.5 block">現在値ベース</span>
              </button>

              <button
                type="button"
                onClick={() => setCalcBasis('cost')}
                className={`p-2.5 rounded-xl border text-left transition-all ${
                  calcBasis === 'cost'
                    ? 'bg-[#171f33] border-[#4edea3] text-white'
                    : 'bg-[#0b1326] border-slate-800 text-[#bbcabf]'
                }`}
              >
                <div className="flex items-center justify-between text-xs font-bold">
                  <span>取得額基準</span>
                  <span className={`material-symbols-outlined text-[16px] ${calcBasis === 'cost' ? 'text-[#4edea3]' : 'text-slate-600'}`}>
                    {calcBasis === 'cost' ? 'radio_button_checked' : 'radio_button_unchecked'}
                  </span>
                </div>
                <span className="text-[10px] text-[#bbcabf] mt-0.5 block">買付単価 (YOC)</span>
              </button>
            </div>
          </div>

          {/* Tax Setting */}
          <div className="border-t border-slate-800 pt-3 flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-white">税金計算設定</p>
              <p className="text-[10px] text-[#bbcabf]">税引後表示 (国内税 20.315% を控除)</p>
            </div>
            <button
              type="button"
              onClick={() => setTaxDeduction(!taxDeduction)}
              className={`w-11 h-6 rounded-full p-0.5 transition-colors ${taxDeduction ? 'bg-[#10b981]' : 'bg-slate-700'}`}
            >
              <span className={`block w-5 h-5 rounded-full bg-white shadow transition-transform ${taxDeduction ? 'translate-x-5' : 'translate-x-0'}`} />
            </button>
          </div>

          {/* US Tax */}
          <div className="border-t border-slate-800 pt-3 flex items-center justify-between">
            <div>
              <div className="flex items-center gap-1.5">
                <p className="text-xs font-semibold text-white">米国株 現地源泉徴収控除 (10%)</p>
                <span className="text-[9px] bg-[#10b981]/15 text-[#4edea3] px-1.5 py-0.2 rounded font-bold">新NISA連動</span>
              </div>
              <p className="text-[10px] text-[#bbcabf]">外国税額控除前の手取り概算に反映</p>
            </div>
            <button
              type="button"
              onClick={() => setUsTaxDeduction(!usTaxDeduction)}
              className={`w-11 h-6 rounded-full p-0.5 transition-colors ${usTaxDeduction ? 'bg-[#10b981]' : 'bg-slate-700'}`}
            >
              <span className={`block w-5 h-5 rounded-full bg-white shadow transition-transform ${usTaxDeduction ? 'translate-x-5' : 'translate-x-0'}`} />
            </button>
          </div>

          {/* Target Yield */}
          <div className="border-t border-slate-800 pt-3">
            <div className="flex items-center justify-between text-xs mb-1.5">
              <span className="font-semibold text-white">目標利回り (ロードマップ試算)</span>
              <span className="font-mono text-sm font-bold text-[#4edea3]">{targetYield}%</span>
            </div>
            <div className="grid grid-cols-3 gap-1.5">
              {['3.5', '4.0', '4.5'].map(y => (
                <button
                  key={y}
                  type="button"
                  onClick={() => setTargetYield(y)}
                  className={`py-1.5 rounded-lg text-xs font-semibold ${
                    targetYield === y
                      ? 'bg-[#10b981] text-[#003824] font-bold shadow'
                      : 'bg-[#0b1326] text-[#bbcabf] border border-slate-800'
                  }`}
                >
                  {y}% {y === '3.5' ? '(堅実)' : y === '4.0' ? '(標準)' : '(積極)'}
                </button>
              ))}
            </div>
          </div>

          {/* Slider */}
          <div className="border-t border-slate-800 pt-3">
            <div className="flex items-center justify-between text-xs mb-1">
              <span className="font-semibold text-white">構成比「その他」集約閾値</span>
              <span className="font-mono text-xs font-bold text-[#4edea3]">{threshold}%</span>
            </div>
            <input
              type="range"
              min="3"
              max="10"
              value={threshold}
              onChange={e => setThreshold(parseInt(e.target.value) || 5)}
              className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer"
            />
          </div>
        </div>
      </section>

      {/* 4. 通知・リマインダー設定 Section */}
      <section className="space-y-2.5">
        <div className="flex items-center gap-2 px-1">
          <span className="material-symbols-outlined text-[#4edea3] text-[20px]">notifications</span>
          <h3 className="text-xs font-bold text-white tracking-wide uppercase">通知・リマインダー設定</h3>
        </div>

        <div className="bg-[#131b2e] rounded-2xl p-4 border border-slate-800 space-y-3 shadow-md">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-white">配当金の権利落ち日・入金通知</p>
              <p className="text-[10px] text-[#bbcabf]">権利付最終日および振込予定日をプッシュ通知</p>
            </div>
            <button
              type="button"
              onClick={() => setNotifyExDiv(!notifyExDiv)}
              className={`w-11 h-6 rounded-full p-0.5 transition-colors ${notifyExDiv ? 'bg-[#10b981]' : 'bg-slate-700'}`}
            >
              <span className={`block w-5 h-5 rounded-full bg-white shadow transition-transform ${notifyExDiv ? 'translate-x-5' : 'translate-x-0'}`} />
            </button>
          </div>

          <div className="border-t border-slate-800 pt-3 flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-white">買場シグナル・株価アラート</p>
              <p className="text-[10px] text-[#bbcabf]">利回り4.5%突破時や年初来安値更新時</p>
            </div>
            <button
              type="button"
              onClick={() => setNotifyBuySignal(!notifyBuySignal)}
              className={`w-11 h-6 rounded-full p-0.5 transition-colors ${notifyBuySignal ? 'bg-[#10b981]' : 'bg-slate-700'}`}
            >
              <span className={`block w-5 h-5 rounded-full bg-white shadow transition-transform ${notifyBuySignal ? 'translate-x-5' : 'translate-x-0'}`} />
            </button>
          </div>
        </div>
      </section>

      {/* 5. セキュリティ＆プライバシー Section */}
      <section className="space-y-2.5">
        <div className="flex items-center gap-2 px-1">
          <span className="material-symbols-outlined text-[#4edea3] text-[20px]">shield</span>
          <h3 className="text-xs font-bold text-white tracking-wide uppercase">セキュリティ＆プライバシー</h3>
        </div>

        <div className="bg-[#131b2e] rounded-2xl p-4 border border-slate-800 space-y-3 shadow-md">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-white">生体認証 (Face ID / 指紋認証)</p>
              <p className="text-[10px] text-[#bbcabf]">アプリ起動時のロック解除</p>
            </div>
            <button
              type="button"
              onClick={() => setBiometrics(!biometrics)}
              className={`w-11 h-6 rounded-full p-0.5 transition-colors ${biometrics ? 'bg-[#10b981]' : 'bg-slate-700'}`}
            >
              <span className={`block w-5 h-5 rounded-full bg-white shadow transition-transform ${biometrics ? 'translate-x-5' : 'translate-x-0'}`} />
            </button>
          </div>

          <div className="border-t border-slate-800 pt-3 flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-white">起動時の金額伏字マスク</p>
              <p className="text-[10px] text-[#bbcabf]">電車内など外出時の画面閲覧用</p>
            </div>
            <button
              type="button"
              onClick={() => setMaskAtStartup(!maskAtStartup)}
              className={`w-11 h-6 rounded-full p-0.5 transition-colors ${maskAtStartup ? 'bg-[#10b981]' : 'bg-slate-700'}`}
            >
              <span className={`block w-5 h-5 rounded-full bg-white shadow transition-transform ${maskAtStartup ? 'translate-x-5' : 'translate-x-0'}`} />
            </button>
          </div>

          <div className="border-t border-slate-800 pt-3 flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-[#ffb4ab]">ローカルデータ完全消去</p>
              <p className="text-[10px] text-[#bbcabf]">端末内の保有・設定データをすべて削除</p>
            </div>
            <button
              type="button"
              onClick={() => {
                if (window.confirm('本当にすべてのデータを初期化しますか？')) {
                  showToast('データを初期化しました', 'delete');
                }
              }}
              className="px-3 py-1 rounded-lg bg-red-950/60 hover:bg-red-900 border border-red-800 text-red-200 text-xs font-bold"
            >
              初期化
            </button>
          </div>
        </div>
      </section>

      {/* 6. アプリ情報・サポート Links */}
      <section className="space-y-2.5">
        <div className="flex items-center gap-2 px-1">
          <span className="material-symbols-outlined text-[#4edea3] text-[20px]">info</span>
          <h3 className="text-xs font-bold text-white tracking-wide uppercase">アプリ情報・サポート</h3>
        </div>

        <div className="bg-[#131b2e] rounded-2xl border border-slate-800 divide-y divide-slate-800 overflow-hidden text-xs">
          <button
            type="button"
            onClick={() => navigateTo('contact-feedback')}
            className="w-full flex items-center justify-between p-3.5 hover:bg-[#171f33] text-white text-left transition-colors"
          >
            <span>お問い合わせ・フィードバック (FAQ)</span>
            <span className="material-symbols-outlined text-[18px] text-[#bbcabf]">chevron_right</span>
          </button>

          <button
            type="button"
            onClick={() => navigateTo('terms-privacy')}
            className="w-full flex items-center justify-between p-3.5 hover:bg-[#171f33] text-white text-left transition-colors"
          >
            <span>利用規約＆プライバシーポリシー</span>
            <span className="material-symbols-outlined text-[18px] text-[#bbcabf]">chevron_right</span>
          </button>

          <div className="flex items-center justify-between p-3.5 bg-[#0b1326] text-[#bbcabf]">
            <span>バージョン</span>
            <span className="font-mono text-xs text-white">v1.2.0 (Build 248)</span>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="pt-2 text-center text-[10px] text-[#bbcabf] space-y-1">
        <div className="flex items-center justify-center gap-1">
          <span className="material-symbols-outlined text-[14px] text-[#4edea3]">lock</span>
          <span>金融データは端末内暗号化保存されています</span>
        </div>
        <p className="font-mono text-slate-500">© 2025 Kurogane Dividend System</p>
      </footer>
    </div>
  );
};
