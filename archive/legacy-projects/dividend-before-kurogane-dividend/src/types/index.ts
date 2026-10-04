export type ScreenType =
  | 'asset-management'
  | 'annual-dividend'
  | 'roadmap'
  | 'watchlist'
  | 'stock-detail'
  | 'settings'
  | 'csv-import'
  | 'contact-feedback'
  | 'terms-privacy'
  | 'jetpack-compose';

export type CategoryType = 'all' | 'japan' | 'us' | 'fund';

export interface Holding {
  id: string;
  code: string;
  name: string;
  sector: string;
  market: string;
  category: 'japan' | 'us' | 'fund';
  price: number;
  currency: '¥' | '$';
  shares: number;
  acqPrice: number;
  dps: number; // dividend per share (annual)
  yieldRate: number; // %
  yoc?: number;
  nextPayoutTiming: string;
  nextPayoutAmount: string;
  dividendMonths: number[]; // e.g. [6, 12] or [2, 5, 8, 11]
  dividendType: string; // e.g. '中間配当', '四半期配当', '再投資型'
  accountType: string; // '新NISA成長枠', '新NISAつみたて投資枠', '特定口座', '一般口座'
  dailyChange: string;
  isPositive: boolean;
}

export interface WatchlistStock {
  code: string;
  name: string;
  market: string;
  sector: string;
  category: 'jp' | 'us';
  price: number;
  currency: '¥' | '$';
  dps: number;
  currentYield: number;
  targetYield: number;
  targetPrice: number;
  dailyChange: string;
  isPositive: boolean;
  payoutMonths: number[];
  payoutTiming: string;
  desc: string;
  alertEnabled: boolean;
  badge?: string;
  defaultShares: number;
}

export type StateSimMode = 'normal' | 'offline' | 'loading' | 'zero' | 'empty';

export type SortCriteria = 'val' | 'gain' | 'div' | 'div_amt' | 'code';
export type SortDirection = 'desc' | 'asc';

export type AccentColor = 'emerald' | 'cyan' | 'blue' | 'amber' | 'purple';
export type ThemeMode = 'dark' | 'light' | 'system';

export interface ValuationScenario {
  id: number;
  cat: 'VALUE_DIV' | 'DEEP_VALUE' | 'GROWTH' | 'CAUTION';
  per: number;
  pbr: number;
  timing: string;
  title: string;
  score: number;
  status: string;
  tag: string;
  lead: string;
  body: string;
}

export interface RoadmapStep {
  step: number;
  level: number;
  name: string;
  title: string;
  description: string;
  icon: string;
  targetMonthly: number;
  targetAnnual: number;
  status: 'completed' | 'in_progress' | 'locked';
  statusLabel: string;
  badge: string;
  currentRate: number;
  rateLabel: string;
  subStatusLabel: string;
  shortageMonthly?: number;
  shortageAnnual?: number;
  neededCapitalSim: {
    yield35?: string;
    yield40?: string;
    yield45?: string;
    status?: string;
  };
}

export interface ToastMessage {
  id: string;
  message: string;
  icon?: string;
  actionText?: string;
  onAction?: () => void;
}
