import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';

export const routes: Routes = [
  { path: '', redirectTo: 'auth/login', pathMatch: 'full' },
  {
    path: 'auth',
    loadComponent: () => import('./features/auth/auth-shell/auth-shell').then((m) => m.AuthShell),
    children: [
      { path: 'login', loadComponent: () => import('./features/auth/login/login').then((m) => m.Login) },
      { path: 'register', loadComponent: () => import('./features/auth/register/register').then((m) => m.Register) },
      {
        path: 'forgot-password',
        loadComponent: () => import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
      },
      {
        path: 'reset-password',
        loadComponent: () => import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
      },
      {
        path: 'two-factor',
        loadComponent: () => import('./features/auth/two-factor/two-factor').then((m) => m.TwoFactor),
      },
      {
        path: 'locked',
        loadComponent: () =>
          import('./features/account-management/account-lock/account-lock').then((m) => m.AccountLock),
      },
      {
        path: 'oauth-callback',
        loadComponent: () => import('./features/auth/oauth-callback/oauth-callback').then((m) => m.OauthCallback),
      },
    ],
  },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/app-shell/app-shell').then((m) => m.AppShell),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/dashboard/dashboard').then((m) => m.Dashboard) },
      {
        path: 'stocks',
        loadComponent: () => import('./features/stocks/stock-list/stock-list').then((m) => m.StockList),
      },
      {
        path: 'stocks/:symbol',
        loadComponent: () => import('./features/stocks/stock-detail/stock-detail').then((m) => m.StockDetail),
      },
      {
        path: 'opportunities',
        loadComponent: () =>
          import('./features/opportunities/opportunities/opportunities').then((m) => m.Opportunities),
      },
      {
        path: 'alerts',
        loadComponent: () => import('./features/alerts/alerts-page/alerts-page').then((m) => m.AlertsPage),
      },
      {
        path: 'watchlist',
        loadComponent: () => import('./features/watchlist/watchlist/watchlist').then((m) => m.Watchlist),
      },
      {
        path: 'portfolio',
        loadComponent: () => import('./features/portfolio/portfolio/portfolio').then((m) => m.Portfolio),
      },
      {
        path: 'exchange-rates',
        loadComponent: () =>
          import('./features/exchange-rates/exchange-rates/exchange-rates').then((m) => m.ExchangeRatesPage),
      },
      {
        path: 'analysis',
        loadComponent: () => import('./features/analysis/analysis/analysis').then((m) => m.Analysis),
      },
      {
        path: 'analysis/:symbol',
        loadComponent: () => import('./features/analysis/analysis/analysis').then((m) => m.Analysis),
      },
      {
        path: 'pipeline',
        loadComponent: () =>
          import('./features/pipeline/pipeline-monitor/pipeline-monitor').then((m) => m.PipelineMonitor),
      },
      {
        path: 'account',
        loadComponent: () => import('./features/users/user-profile/user-profile').then((m) => m.UserProfile),
      },
    ],
  },
];
