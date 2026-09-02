import { Routes } from '@angular/router';

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
    ],
  },
];
