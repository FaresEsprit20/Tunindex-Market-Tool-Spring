import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Card } from '../../../shared/components/card/card';
import { Registration } from '../../../core/services/registration';

type Strength = 'weak' | 'medium' | 'strong';

function passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
  const password = control.get('password')?.value;
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

// Tunisian mobile numbers: 8 digits, commonly grouped as 2-3-3.
const TUNISIAN_PHONE_PATTERN = /^\d{8}$/;

// Mirrors the backend's FieldsValidation.USERNAME_REGEX.
const USERNAME_PATTERN = /^[a-zA-Z0-9_.-]{3,30}$/;

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, Card],
  templateUrl: './register.html',
  styleUrl: './register.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly registration = inject(Registration);

  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly created = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly maxBirthDate = todayIsoDate();

  protected readonly form = this.fb.nonNullable.group(
    {
      firstName: ['', [Validators.required]],
      lastName: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
      username: ['', [Validators.pattern(USERNAME_PATTERN)]],
      birthDate: ['', [Validators.required]],
      phone: ['', [Validators.required, Validators.pattern(TUNISIAN_PHONE_PATTERN)]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]],
      acceptTerms: [false, [Validators.requiredTrue]],
    },
    { validators: passwordsMatchValidator },
  );

  private readonly passwordValue = toSignal(this.form.controls.password.valueChanges, {
    initialValue: '',
  });

  private readonly strengthScore = computed(() => scorePassword(this.passwordValue()));
  protected readonly strength = computed<Strength | null>(() => {
    if (!this.passwordValue()) return null;
    const score = this.strengthScore();
    if (score <= 2) return 'weak';
    if (score <= 3) return 'medium';
    return 'strong';
  });
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

    const { firstName, lastName, email, username, birthDate, phone, password } = this.form.getRawValue();

    this.registration.create({ firstName, lastName, email, username, birthDate, phone: `+216${phone}`, password }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.created.set(true);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const backendErrors = err instanceof HttpErrorResponse ? (err.error?.errors as string[] | undefined) : undefined;
        const backendMessage = err instanceof HttpErrorResponse ? (err.error?.message as string | undefined) : undefined;
        this.errorMessage.set(
          backendErrors?.join(' ') ?? backendMessage ?? 'Could not create your account. That email may already be registered.',
        );
      },
    });
  }
}
