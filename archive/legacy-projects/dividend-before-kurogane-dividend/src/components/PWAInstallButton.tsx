import React, { useState } from 'react';
import { usePWAInstall } from './usePWAInstall';

export const PWAInstallButton: React.FC = () => {
  const { isInstallable, isInstalled, isIOS, install } = usePWAInstall();
  const [showIOSGuide, setShowIOSGuide] = useState(false);

  // If already running as an installed PWA, hide the button
  if (isInstalled) {
    return null;
  }

  // Chromium / Android / Desktop flow
  if (isInstallable) {
    return (
      <button
        onClick={install}
        className="flex items-center gap-1.5 px-3 py-1.5 bg-accent hover:brightness-105 active:scale-95 text-[#003824] rounded-xl text-xs font-black shadow-md shadow-accent/20 transition-all"
      >
        <span className="material-symbols-outlined text-[16px]">install_mobile</span>
        <span>アプリをスマホにインストール</span>
      </button>
    );
  }

  // iOS Safari flow (beforeinstallprompt is not supported by WebKit)
  if (isIOS) {
    return (
      <>
        <button
          onClick={() => setShowIOSGuide(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-accent hover:brightness-105 active:scale-95 text-[#003824] rounded-xl text-xs font-black shadow-md shadow-accent/20 transition-all"
        >
          <span className="material-symbols-outlined text-[16px]">install_mobile</span>
          <span>iOS アプリをインストール</span>
        </button>

        {showIOSGuide && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
            <div className="w-full max-w-sm rounded-2xl bg-[#131b2e] border border-accent/30 p-5 shadow-2xl text-left">
              <h3 className="text-sm font-black text-white flex items-center gap-1.5">
                <span className="material-symbols-outlined text-accent text-[20px]">smartphone</span>
                <span>iPhone / iPad に追加する方法</span>
              </h3>
              <p className="mt-3 text-xs text-[#bbcabf] leading-relaxed space-y-2">
                1. Safariブラウザのツールバーにある「<strong>共有</strong> (下から上へ矢印が飛び出したアイコン)」アイコンをタップします。<br />
                <br />
                2. 表示されたメニューを下にスクロールし、「<strong>ホーム画面に追加</strong>」を選択します。<br />
                <br />
                3. 右上の「<strong>追加</strong>」をタップすると、ホーム画面にこのアプリ（Kurogane）が本物のアプリのようにインストールされます。
              </p>
              <button
                onClick={() => setShowIOSGuide(false)}
                className="mt-4 w-full h-10 rounded-xl bg-accent text-[#003824] text-xs font-extrabold hover:brightness-105 transition-all"
              >
                了解しました
              </button>
            </div>
          </div>
        )}
      </>
    );
  }

  // Fallback default installer option for browsers that don't emit the prompt automatically
  return (
    <button
      onClick={() => {
        alert('Google Chrome、または Safari ブラウザのメニューから「ホーム画面に追加」または「アプリをインストール」をタップすると、スマホアプリとしてインストールできます。');
      }}
      className="flex items-center gap-1.5 px-3 py-1.5 bg-[#171f33] hover:bg-slate-800 text-accent border border-accent/30 rounded-xl text-xs font-bold transition-all"
    >
      <span className="material-symbols-outlined text-[16px]">install_mobile</span>
      <span>スマホアプリとして使う</span>
    </button>
  );
};
