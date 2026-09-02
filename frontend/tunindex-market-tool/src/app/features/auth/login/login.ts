import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { InfoHint } from '../../../shared/components/info-hint/info-hint';
import { Card } from '../../../shared/components/card/card';
import { Auth } from '../../../core/services/auth';
import { Notification } from '../../../core/services/notification';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, Card, InfoHint],
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(Auth);
  private readonly notification = inject(Notification);
  private readonly router = inject(Router);

  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    login: ['', [Validators.required]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    rememberDevice: [true],
  });

  protected togglePasswordVisibility(): void {
    this.showPassword.update((visible) => !visible);
  }

  protected onSubmit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);

    const { login, password, rememberDevice } = this.form.getRawValue();

    this.auth.login({ login, password, rememberDevice }).subscribe({
      next: (res) => {
        this.submitting.set(false);
        if (res.requiresTwoFactor) {
          this.router.navigateByUrl('/auth/two-factor');
          return;
        }
        this.notification.show('Signed in', `Welcome back, ${login}.`, 'success');
        this.router.navigateByUrl('/app/dashboard');
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        // The backend has a clean JSON error body for bad credentials, but
        // a locked account throws an uncaught Spring Security exception
        // with no reliable shape — fall back to a generic message rather
        // than guess at a status code that isn't consistently produced.
        const backendMessage = err instanceof HttpErrorResponse ? (err.error?.message as string | undefined) : undefined;
        this.errorMessage.set(backendMessage ?? 'Invalid email or password.');
      },
    });
  }

  protected continueWithGoogle(): void {
    // Full browser navigation, not fetch/XHR: the backend runs Google's
    // real OAuth2 redirect chain server-side and expects the browser
    // itself to follow it, not an API client to consume a JSON response.
    this.auth.getGoogleLoginUrl().subscribe({
      next: (res) => {
        window.location.href = res.login_url;
      },
      error: () => {
        this.notification.show('Google sign-in unavailable', 'Could not reach the sign-in service. Try again shortly.', 'error');
      },
    });
  }
}
