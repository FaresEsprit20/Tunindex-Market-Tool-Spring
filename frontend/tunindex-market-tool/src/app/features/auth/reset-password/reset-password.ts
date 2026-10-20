import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Card } from '../../../shared/components/card/card';
import { PasswordReset } from '../../../core/services/password-reset';

type Strength = 'weak' | 'medium' | 'strong';

function passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
  const password = control.get('newPassword')?.value;
  const confirm = control.get('confirmPassword')?.value;
  return password && confirm && password !== confirm ? { mismatch: true } : null;
}

function scorePassword(value: string): number {
  let score = 0;
  if (value.length >= 8) score++;
  if (/[a-z]/.test(value)) score++;
  if (/[A-Z]/.test(value)) score++;
  if (/[0-9]/.test(value)) score++;
  if (/[^a-zA-Z0-9]/.test(value)) score++;
  return score;
}

@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink, Card],
  templateUrl: './reset-password.html',
  styleUrl: './reset-password.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPassword {
  private readonly fb = inject(FormBuilder);
  private readonly passwordReset = inject(PasswordReset);
  private readonly route = inject(ActivatedRoute);

  // Reactive, not a one-time snapshot read: Angular reuses this component
  // instance across same-route navigations (e.g. opening a second reset
  // link while this page is already open), so a snapshot read would go
  // stale silently.
  private readonly queryParamMap = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly token = computed(() => this.queryParamMap().get('token'));

  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly done = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group(
    {
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatchValidator },
  );

  private readonly newPasswordValue = toSignal(this.form.controls.newPassword.valueChanges, {
    initialValue: '',
  });

  private readonly strengthScore = computed(() => scorePassword(this.newPasswordValue()));
  protected readonly strength = computed<Strength | null>(() => {
    if (!this.newPasswordValue()) return null;
    const score = this.strengthScore();
    if (score <= 2) return 'weak';
    if (score <= 3) return 'medium';
    return 'strong';
  });
  // 1-3 segments lit, scaled from the 0-5 raw score.
  protected readonly litSegments = computed(() => Math.min(3, Math.ceil((this.strengthScore() / 5) * 3)));

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

    const { newPassword } = this.form.getRawValue();
    const tokenValue = this.token() ?? '';

    this.passwordReset.confirmReset(tokenValue, newPassword).subscribe({
      next: () => {
        this.submitting.set(false);
        this.done.set(true);
      },
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set('This reset link is invalid or has expired.');
      },
    });
  }
}
