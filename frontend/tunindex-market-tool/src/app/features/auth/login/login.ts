import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
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

  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
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

    const { email, password, rememberDevice } = this.form.getRawValue();

    this.auth.login({ email, password, rememberDevice }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.notification.show('Signed in', `Welcome back, ${email}.`, 'success');
      },
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set('Invalid email or password.');
      },
    });
  }

  protected continueWithGoogle(): void {
    this.notification.show('Google sign-in', 'Backend integration for OAuth2 is pending.', 'info');
  }
}
