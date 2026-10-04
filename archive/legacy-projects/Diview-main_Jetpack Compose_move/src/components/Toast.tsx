import React from 'react';
import { useApp } from '../context/AppContext';

export const Toast: React.FC = () => {
  const { toast, clearToast } = useApp();

  if (!toast) return null;

  return (
    <div className="fixed top-18 left-1/2 -translate-x-1/2 z-50 flex items-center justify-center w-11/12 max-w-sm pointer-events-none transition-all duration-300">
      <div className="flex items-center justify-between gap-3 px-4 py-2.5 rounded-full bg-[#171f33]/95 text-white shadow-2xl backdrop-blur-xl border border-slate-700/80 pointer-events-auto text-xs font-semibold animate-in fade-in slide-in-from-top-2 duration-200">
        <div className="flex items-center gap-2 min-w-0">
          <span className="material-symbols-outlined text-[18px] text-[#4edea3] shrink-0">
            {toast.icon || 'check_circle'}
          </span>
          <span className="truncate">{toast.message}</span>
        </div>

        {toast.actionText && (
          <button
            type="button"
            onClick={() => {
              if (toast.onAction) toast.onAction();
              clearToast();
            }}
            className="px-2.5 py-0.5 rounded-full bg-[#10b981] text-[#003824] text-[11px] font-bold hover:brightness-110 active:scale-95 shrink-0"
          >
            {toast.actionText}
          </button>
        )}
      </div>
    </div>
  );
};
