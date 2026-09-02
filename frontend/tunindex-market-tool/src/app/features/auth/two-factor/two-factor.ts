import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, inject, signal, viewChildren } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Card } from '../../../shared/components/card/card';
import { Auth } from '../../../core/services/auth';
import { Notification } from '../../../core/services/notification';

const CODE_LENGTH = 6;
// Matches the backend's TOTP_LOGIN_PENDING ticket lifetime (see
// UnifiedToken.onCreate on the api module) — this is how long the whole
// login attempt stays valid, not a code-rotation timer: the code itself
// refreshes every 30s on the authenticator app, independent of this page.
const SESSION_TTL_SECONDS = 300;

@Component({
  selector: 'app-two-factor',
  imports: [RouterLink, Card],
  templateUrl: './two-factor.html',
  styleUrl: './two-factor.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TwoFactor {
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(Auth);
  private readonly notification = inject(Notification);
  private readonly router = inject(Router);

  protected readonly digitInputs = viewChildren<ElementRef<HTMLInputElement>>('digitInput');

  protected readonly digits = signal<string[]>(Array(CODE_LENGTH).fill(''));
  protected readonly code = computed(() => this.digits().join(''));
  protected readonly isComplete = computed(() => this.code().length === CODE_LENGTH);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly secondsRemaining = signal(SESSION_TTL_SECONDS);
  protected readonly expiryLabel = computed(() => formatMmSs(this.secondsRemaining()));

  constructor() {
    if (!this.auth.pendingMfaToken()) {
      // Direct navigation with no login attempt in flight — nothing to verify.
      void this.router.navigateByUrl('/auth/login');
      return;
    }

    const intervalId = setInterval(() => {
      this.secondsRemaining.update((s) => Math.max(0, s - 1));
    }, 1000);
    this.destroyRef.onDestroy(() => clearInterval(intervalId));
  }

  protected onDigitInput(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = input.value.replace(/\D/g, '').slice(-1);

    this.digits.update((current) => {
      const next = [...current];
      next[index] = value;
      return next;
    });
    input.value = value;

    if (value && index < CODE_LENGTH - 1) {
      this.digitInputs()[index + 1]?.nativeElement.focus();
    }
  }

  protected onDigitKeydown(index: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace' && !this.digits()[index] && index > 0) {
      // Without this, the browser's native backspace-delete fires on
      // whichever input ends up focused after our .focus() call below,
      // silently clearing the previous box's digit too.
      event.preventDefault();
      this.digitInputs()[index - 1]?.nativeElement.focus();
    }
  }

  protected onPaste(event: ClipboardEvent): void {
    const pasted = event.clipboardData?.getData('text').replace(/\D/g, '').slice(0, CODE_LENGTH);
    if (!pasted) return;
    event.preventDefault();

    const next = Array(CODE_LENGTH).fill('');
    for (let i = 0; i < pasted.length; i++) next[i] = pasted[i];
    this.digits.set(next);

    const inputs = this.digitInputs();
    const focusIndex = Math.min(pasted.length, CODE_LENGTH - 1);
    inputs[focusIndex]?.nativeElement.focus();
  }

  protected onSubmit(): void {
    if (this.submitting() || !this.isComplete()) {
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);

    this.auth.verifyTwoFactor(this.code()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.notification.show('Signed in', 'Two-factor verification successful.', 'success');
        void this.router.navigateByUrl('/app/dashboard');
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const backendMessage = err instanceof HttpErrorResponse ? (err.error?.message as string | undefined) : undefined;
        this.errorMessage.set(backendMessage ?? 'That code is incorrect or has expired.');
        this.digits.set(Array(CODE_LENGTH).fill(''));
        this.digitInputs()[0]?.nativeElement.focus();
      },
    });
  }
}

function formatMmSs(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}
