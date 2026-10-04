import React, { useState } from 'react';
import { composeFiles, ComposeFileItem } from '../data/composeCodeFiles';
import { useApp } from '../context/AppContext';

export const JetpackComposeScreen: React.FC = () => {
  const { navigateTo, showToast } = useApp();
  const [selectedFileId, setSelectedFileId] = useState<string>('MainActivity');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [isCopied, setIsCopied] = useState<boolean>(false);
  const [showGuide, setShowGuide] = useState<boolean>(false);

  const filteredFiles = composeFiles.filter((f) => {
    if (selectedCategory === 'all') return true;
    if (selectedCategory === 'screen') return f.category === 'screen';
    if (selectedCategory === 'viewmodel') return f.category === 'viewmodel' || f.category === 'model';
    if (selectedCategory === 'config') return f.category === 'config' || f.category === 'theme';
    return true;
  });

  const activeFile = composeFiles.find((f) => f.id === selectedFileId) || composeFiles[0];

  const handleCopy = () => {
    navigator.clipboard.writeText(activeFile.code);
    setIsCopied(true);
    showToast(`${activeFile.name} をクリップボードにコピーしました`);
    setTimeout(() => setIsCopied(false), 2000);
  };

  const handleCopyAll = () => {
    const allCode = composeFiles.map((f) => `// ======================\n// FILE: ${f.path}\n// ======================\n\n${f.code}\n`).join('\n\n');
    navigator.clipboard.writeText(allCode);
    showToast('全Kotlin/Composeソースコードを一括コピーしました');
  };

  return (
    <div className="pb-24 pt-2">
      {/* Top Banner */}
      <div className="px-4 mb-4">
        <div className="p-4 rounded-2xl bg-gradient-to-br from-[#10b981]/15 via-[#171f33] to-[#0b1326] border border-[#10b981]/30 shadow-lg shadow-[#10b981]/5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#10b981]/20 border border-[#10b981]/40 flex items-center justify-center text-xl">
                🤖
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-white font-bold text-base">Jetpack Compose ソースコード</h2>
                  <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#10b981] text-black">
                    Kotlin / M3
                  </span>
                </div>
                <p className="text-[11px] text-[#bbcabf] mt-0.5">
                  Android実機・Android Studioでそのまま動作する公式Material 3準拠コード
                </p>
              </div>
            </div>
            <button
              onClick={() => setShowGuide(!showGuide)}
              className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-[#222a3d] hover:bg-[#2d3449] text-white border border-white/10 flex items-center gap-1.5 transition-colors cursor-pointer"
            >
              <span>📖 導入手順</span>
            </button>
          </div>

          {/* Quick Stats & Action */}
          <div className="mt-3 pt-3 border-t border-white/10 flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-4 text-xs text-[#bbcabf]">
              <span>構成ファイル: <strong className="text-white">{composeFiles.length} ファイル</strong></span>
              <span>•</span>
              <span>フレームワーク: <strong className="text-emerald-400">Jetpack Compose BOM 2024.10</strong></span>
            </div>
            <button
              onClick={handleCopyAll}
              className="text-xs px-3 py-1 rounded-md bg-[#10b981]/20 hover:bg-[#10b981]/30 text-[#4edea3] font-bold border border-[#10b981]/40 transition cursor-pointer"
            >
              ⚡ 全ファイルを一括コピー
            </button>
          </div>
        </div>
      </div>

      {/* Guide Accordion */}
      {showGuide && (
        <div className="px-4 mb-4">
          <div className="p-4 rounded-2xl bg-[#131b2e] border border-cyan-500/30 text-xs text-[#dae2fd] space-y-3">
            <div className="flex items-center justify-between">
              <span className="font-bold text-cyan-400 text-sm">📱 Android Studio でのビルド＆実行ガイド</span>
              <button onClick={() => setShowGuide(false)} className="text-[#bbcabf] hover:text-white">✕</button>
            </div>
            <ol className="list-decimal list-inside space-y-2 text-[#bbcabf] leading-relaxed">
              <li>
                <strong className="text-white">Android Studio</strong> を起動し、<span className="text-emerald-400">「New Project」→「Empty Activity」</span>を選択します。
              </li>
              <li>
                Package Name に <code className="px-1.5 py-0.5 rounded bg-black/40 text-emerald-300 font-mono">com.kurogane.dividend</code> を指定します。
              </li>
              <li>
                <code className="text-amber-300 font-mono">app/build.gradle.kts</code> に下記の依存関係（BOM、Navigation Compose、Material3 Icons）を追加します。
              </li>
              <li>
                本画面下の各タブのコード（<code className="text-white">MainActivity.kt</code>、<code className="text-white">ui/screens/*</code> 等）をプロジェクトの該当ディレクトリに配置します。
              </li>
              <li>
                右上の <span className="text-emerald-400 font-bold">▶ Run (Shift + F10)</span> をクリックしてエミュレータまたは実機で起動します。
              </li>
            </ol>
          </div>
        </div>
      )}

      {/* Category Pills */}
      <div className="px-4 mb-3">
        <div className="flex gap-1.5 overflow-x-auto no-scrollbar py-1">
          {[
            { id: 'all', label: 'すべてのファイル' },
            { id: 'screen', label: 'UI Screens' },
            { id: 'viewmodel', label: 'ViewModel & Models' },
            { id: 'config', label: 'Config & Theme' },
          ].map((cat) => (
            <button
              key={cat.id}
              onClick={() => setSelectedCategory(cat.id)}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition cursor-pointer ${
                selectedCategory === cat.id
                  ? 'bg-[#10b981] text-black shadow-sm'
                  : 'bg-[#171f33] text-[#bbcabf] hover:text-white border border-white/5'
              }`}
            >
              {cat.label}
            </button>
          ))}
        </div>
      </div>

      {/* File Horizontal Tabs */}
      <div className="px-4 mb-3">
        <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1">
          {filteredFiles.map((file) => (
            <button
              key={file.id}
              onClick={() => setSelectedFileId(file.id)}
              className={`px-3 py-2 rounded-xl text-xs font-mono whitespace-nowrap flex items-center gap-2 transition cursor-pointer ${
                selectedFileId === file.id
                  ? 'bg-[#222a3d] text-[#4edea3] border border-[#10b981]/50 font-bold shadow'
                  : 'bg-[#131b2e] text-[#bbcabf] hover:text-white border border-white/5'
              }`}
            >
              <span className="text-[13px]">
                {file.name.endsWith('.kt') ? '🔷' : file.name.endsWith('.xml') ? '📄' : '⚙️'}
              </span>
              <span>{file.name}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Active Code Display Card */}
      <div className="px-4">
        <div className="rounded-2xl bg-[#0e1628] border border-white/10 shadow-2xl overflow-hidden">
          {/* File Header Bar */}
          <div className="px-4 py-3 bg-[#131b2e] border-b border-white/10 flex items-center justify-between">
            <div className="flex items-center gap-2 overflow-hidden">
              <span className="text-emerald-400 font-mono text-xs font-bold">FILE</span>
              <span className="text-[#bbcabf] font-mono text-xs truncate">
                {activeFile.path}
              </span>
            </div>
            <button
              onClick={handleCopy}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition cursor-pointer ${
                isCopied
                  ? 'bg-emerald-500 text-black'
                  : 'bg-[#10b981] hover:bg-[#10b981]/90 text-black'
              }`}
            >
              <span>{isCopied ? '✓ コピー完了' : '📋 コードをコピー'}</span>
            </button>
          </div>

          {/* File Description */}
          <div className="px-4 py-2 bg-[#171f33]/60 border-b border-white/5 text-[11px] text-[#bbcabf]">
            {activeFile.description}
          </div>

          {/* Syntax Highlighted Code Viewer */}
          <div className="relative font-mono text-xs overflow-x-auto p-4 max-h-[600px] overflow-y-auto leading-relaxed select-text bg-[#060e20]">
            <pre className="text-[#dae2fd]">
              <code>{activeFile.code}</code>
            </pre>
          </div>
        </div>
      </div>

      {/* Bottom Switch back to App Preview CTA */}
      <div className="px-4 mt-6">
        <div className="p-4 rounded-xl bg-[#171f33] border border-white/10 flex items-center justify-between">
          <div>
            <div className="text-white font-bold text-sm">📱 アプリ動作を画面で確認する</div>
            <div className="text-xs text-[#bbcabf]">Vite SPA上で動作するインタラクティブプレビューに戻ります</div>
          </div>
          <button
            onClick={() => navigateTo('asset-management')}
            className="px-4 py-2 rounded-xl bg-gradient-to-r from-[#10b981] to-[#06b6d4] text-black font-bold text-xs shadow-md transition hover:opacity-90 cursor-pointer"
          >
            プレビュー画面へ
          </button>
        </div>
      </div>
    </div>
  );
};
