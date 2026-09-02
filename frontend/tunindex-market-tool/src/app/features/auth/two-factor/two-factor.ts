import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, inject, signal, viewChildren } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Card } from '../../../shared/components/card/card';
import { TwoFactorAuth } from '../../../core/services/two-factor-auth';
import { Notification } from '../../../core/services/notification';

const CODE_LENGTH = 6;
const RESEND_COOLDOWN_SECONDS = 60;
const CODE_TTL_SECONDS = 180;

@Component({
  selector: 'app-two-factor',
  imports: [RouterLink, Card],
  templateUrl: './two-factor.html',
  styleUrl: './two-factor.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TwoFactor {
  private readonly destroyRef = inject(DestroyRef);
  private readonly twoFactorAuth = inject(TwoFactorAuth);
  private readonly notification = inject(Notification);

  protected readonly digitInputs = viewChildren<ElementRef<HTMLInputElement>>('digitInput');

  protected readonly digits = signal<string[]>(Array(CODE_LENGTH).fill(''));
  protected readonly code = computed(() => this.digits().join(''));
  protected readonly isComplete = computed(() => this.code().length === CODE_LENGTH);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly secondsUntilExpiry = signal(CODE_TTL_SECONDS);
  protected readonly resendCooldown = signal(RESEND_COOLDOWN_SECONDS);
  protected readonly canResend = computed(() => this.resendCooldown() <= 0);

  protected readonly expiryLabel = computed(() => formatMmSs(this.secondsUntilExpiry()));

  constructor() {
    const intervalId = setInterval(() => {
      this.secondsUntilExpiry.update((s) => Math.max(0, s - 1));
      this.resendCooldown.update((s) => Math.max(0, s - 1));
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

    this.twoFactorAuth.verify(this.code()).subscribe({
      next: () => {
        this.submitting.set(false);
      },
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set('That code is incorrect or has expired.');
      },
    });
  }

  protected onResend(): void {
    if (!this.canResend()) {
      return;
    }

    this.twoFactorAuth.resend().subscribe(() => {
      this.resendCooldown.set(RESEND_COOLDOWN_SECONDS);
      this.secondsUntilExpiry.set(CODE_TTL_SECONDS);
      this.digits.set(Array(CODE_LENGTH).fill(''));
      this.digitInputs()[0]?.nativeElement.focus();
      this.notification.show('Code sent', 'A new verification code was sent to your device.', 'success');
    });
  }
}

function formatMmSs(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}
