import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { toDataURL } from 'qrcode';
import { UserExtendedDto } from '../../../core/models/user.model';
import { TotpSetup, TwoFactorMethod, TwoFactorMethodChange } from '../../../core/models/totp.model';
import { User } from '../../../core/services/user';
import { TwoFactorSetup } from '../../../core/services/two-factor-setup';
import { Notification } from '../../../core/services/notification';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';

function passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
  const password = control.get('password')?.value;
  const confirm = control.get('confirmPassword')?.value;
  return password && confirm && password !== confirm ? { mismatch: true } : null;
}

@Component({
  selector: 'app-user-profile',
  imports: [ReactiveFormsModule, SkeletonBlock],
  templateUrl: './user-profile.html',
  styleUrl: './user-profile.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserProfile {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(User);
  private readonly twoFactorSetup = inject(TwoFactorSetup);
  private readonly notification = inject(Notification);

  protected readonly loading = signal(true);
  protected readonly user = signal<UserExtendedDto | null>(null);
  protected readonly savingProfile = signal(false);
  /** Why the account could not be loaded, shown instead of a blank page. */
  protected readonly loadError = signal<string | null>(null);
  protected readonly savingPassword = signal(false);

  // Two-factor auth (TOTP) enrollment state.
  protected readonly twoFactorEnabled = signal(false);
  protected readonly settingUp = signal(false);
  protected readonly setupData = signal<TotpSetup | null>(null);
  protected readonly qrDataUrl = signal<string | null>(null);
  protected readonly confirmCode = signal('');
  protected readonly disableCode = signal('');
  protected readonly showDisableForm = signal(false);
  protected readonly totpBusy = signal(false);
  protected readonly totpError = signal<string | null>(null);

  // Delivery-method switching. Separate from enable/disable: the factor stays
  // on throughout, only the channel changes.
  protected readonly currentMethod = signal<TwoFactorMethod>('TOTP');
  protected readonly pendingMethod = signal<TwoFactorMethod | null>(null);
  protected readonly methodChange = signal<TwoFactorMethodChange | null>(null);
  protected readonly methodQrDataUrl = signal<string | null>(null);
  protected readonly methodCode = signal('');
  protected readonly methodBusy = signal(false);
  protected readonly methodError = signal<string | null>(null);

  protected readonly profileForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    numTel: ['', [Validators.required]],
    address1: [''],
    address2: [''],
    city: [''],
    zipCode: [''],
    country: [''],
  });

  protected readonly passwordForm = this.fb.nonNullable.group(
    {
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatchValidator },
  );

  constructor() {
    this.loadUser();
  }

  /** Re-runs the fetch, so a transient failure does not need a page reload. */
  protected reload(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.loadUser();
  }

  private loadUser(): void {
    this.userService.getAuthUser().subscribe({
      next: (u) => {
        this.user.set(u);
        this.twoFactorEnabled.set(u.twoFactorEnabled);
        this.loadTwoFactorStatus();
        this.profileForm.patchValue({
          firstName: u.firstName,
          lastName: u.lastName,
          numTel: u.numTel,
          address1: u.address?.address1 ?? '',
          address2: u.address?.address2 ?? '',
          city: u.address?.city ?? '',
          zipCode: u.address?.zipCode ?? '',
          country: u.address?.country ?? '',
        });
        this.loading.set(false);
      },
      error: (err: unknown) => {
        // Previously swallowed, which is what made the page render empty
        // rather than explain itself. A 401 here usually means the session
        // outlived its token - the sign-in is stale, not the account.
        const status = err instanceof HttpErrorResponse ? err.status : 0;
        this.loadError.set(
          status === 401 || status === 403
            ? 'Your session has expired. Please sign out and sign in again.'
            : 'Your account details could not be loaded. Please try again.',
        );
        this.loading.set(false);
      },
    });
  }


  /**
   * Reads which delivery method is in force.
   *
   * <p>Fetched separately from the profile because the account payload only
   * carries the on/off flag - it predates methods existing at all.
   */
  private loadTwoFactorStatus(): void {
    this.twoFactorSetup.getStatus().subscribe({
      next: (status) => {
        this.currentMethod.set(status.method ?? 'TOTP');
        this.pendingMethod.set(status.pendingMethod ?? null);
      },
      // A failure here costs only the method label, so the rest of the page
      // should still render.
      error: () => this.currentMethod.set('TOTP'),
    });
  }

  protected onMethodCodeInput(value: string): void {
    this.methodCode.set(value.replace(/\D/g, '').slice(0, 6));
  }

  /**
   * Starts moving to the other delivery method.
   *
   * <p>Nothing changes on the account until the code is confirmed, so a user
   * who cannot receive on the new channel simply keeps the old one.
   */
  protected startMethodChange(method: TwoFactorMethod): void {
    if (this.methodBusy()) {
      return;
    }
    this.methodBusy.set(true);
    this.methodError.set(null);
    this.methodCode.set('');
    this.methodQrDataUrl.set(null);

    this.twoFactorSetup.startMethodChange(method).subscribe({
      next: (change) => {
        this.methodBusy.set(false);
        this.methodChange.set(change);
        this.pendingMethod.set(change.pendingMethod);

        if (!change.codeDelivered) {
          // The server could not send the code, and said so rather than
          // pretending. Surfacing that beats "check your inbox" for a
          // message that never left.
          this.methodError.set(change.message ?? 'We could not send the code.');
          this.methodChange.set(null);
          return;
        }
        if (change.otpAuthUri) {
          toDataURL(change.otpAuthUri, { width: 220, margin: 1 })
            .then((url) => this.methodQrDataUrl.set(url))
            .catch(() => this.methodQrDataUrl.set(null));
        }
      },
      error: (err: unknown) => {
        this.methodBusy.set(false);
        this.methodError.set(this.extractError(err) ?? 'Could not start the change.');
      },
    });
  }

  protected confirmMethodChange(): void {
    if (this.methodCode().length !== 6 || this.methodBusy()) {
      return;
    }
    this.methodBusy.set(true);
    this.methodError.set(null);

    this.twoFactorSetup.confirmMethodChange(this.methodCode()).subscribe({
      next: () => {
        const applied = this.pendingMethod();
        this.methodBusy.set(false);
        if (applied) {
          this.currentMethod.set(applied);
        }
        this.pendingMethod.set(null);
        this.methodChange.set(null);
        this.methodCode.set('');
        this.methodQrDataUrl.set(null);
        this.notification.show(
          'Method updated',
          applied === 'EMAIL'
            ? 'Codes will now be sent to your email.'
            : 'Codes will now come from your authenticator app.',
          'success',
        );
      },
      error: (err: unknown) => {
        this.methodBusy.set(false);
        this.methodError.set(this.extractError(err) ?? 'That code was not accepted.');
      },
    });
  }

  protected cancelMethodChange(): void {
    this.methodChange.set(null);
    this.pendingMethod.set(null);
    this.methodCode.set('');
    this.methodQrDataUrl.set(null);
    this.methodError.set(null);
  }

  protected startTwoFactorSetup(): void {
    this.totpError.set(null);
    this.settingUp.set(true);
    this.twoFactorSetup.beginSetup().subscribe({
      next: (setup) => {
        this.setupData.set(setup);
        toDataURL(setup.otpAuthUri, { width: 220, margin: 1 })
          .then((url) => this.qrDataUrl.set(url))
          .catch(() => this.qrDataUrl.set(null));
      },
      error: (err: unknown) => {
        this.settingUp.set(false);
        this.totpError.set(this.extractError(err) ?? 'Could not start two-factor setup.');
      },
    });
  }

  protected cancelTwoFactorSetup(): void {
    this.settingUp.set(false);
    this.setupData.set(null);
    this.qrDataUrl.set(null);
    this.confirmCode.set('');
    this.totpError.set(null);
  }

  protected confirmTwoFactorSetup(): void {
    if (this.confirmCode().trim().length !== 6 || this.totpBusy()) {
      return;
    }
    this.totpBusy.set(true);
    this.totpError.set(null);

    this.twoFactorSetup.confirmSetup(this.confirmCode().trim()).subscribe({
      next: () => {
        this.totpBusy.set(false);
        this.twoFactorEnabled.set(true);
        this.settingUp.set(false);
        this.setupData.set(null);
        this.qrDataUrl.set(null);
        this.confirmCode.set('');
        this.notification.show('Two-factor auth enabled', 'Your account is now protected with an authenticator app.', 'success');
      },
      error: (err: unknown) => {
        this.totpBusy.set(false);
        this.totpError.set(this.extractError(err) ?? 'That code is incorrect or has expired.');
      },
    });
  }

  protected onConfirmCodeInput(value: string): void {
    this.confirmCode.set(value.replace(/\D/g, '').slice(0, 6));
  }

  protected onDisableCodeInput(value: string): void {
    this.disableCode.set(value.replace(/\D/g, '').slice(0, 6));
  }

  protected toggleDisableForm(): void {
    this.showDisableForm.update((v) => !v);
    this.disableCode.set('');
    this.totpError.set(null);
  }

  protected disableTwoFactor(): void {
    if (this.disableCode().trim().length !== 6 || this.totpBusy()) {
      return;
    }
    this.totpBusy.set(true);
    this.totpError.set(null);

    this.twoFactorSetup.disable(this.disableCode().trim()).subscribe({
      next: () => {
        this.totpBusy.set(false);
        this.twoFactorEnabled.set(false);
        this.showDisableForm.set(false);
        this.disableCode.set('');
        this.notification.show('Two-factor auth disabled', 'Your account no longer requires a code at sign-in.', 'success');
      },
      error: (err: unknown) => {
        this.totpBusy.set(false);
        this.totpError.set(this.extractError(err) ?? 'That code is incorrect or has expired.');
      },
    });
  }

  private extractError(err: unknown): string | null {
    if (err instanceof HttpErrorResponse) {
      const errors = err.error?.errors as string[] | undefined;
      const message = err.error?.message as string | undefined;
      return errors?.join(' ') ?? message ?? null;
    }
    return null;
  }

  protected saveProfile(): void {
    const currentUser = this.user();
    if (!currentUser || this.profileForm.invalid || this.savingProfile()) {
      this.profileForm.markAllAsTouched();
      return;
    }

    this.savingProfile.set(true);
    const { firstName, lastName, numTel, address1, address2, city, zipCode, country } = this.profileForm.getRawValue();

    this.userService
      .updateProfile({
        email: currentUser.email,
        firstName,
        lastName,
        numTel,
        photo: currentUser.photo ?? '',
        address: { address1, address2, city, zipCode, country },
      })
      .subscribe({
        next: (updated) => {
          this.savingProfile.set(false);
          this.user.update((u) => (u ? { ...u, firstName: updated.firstName, lastName: updated.lastName } : u));
          this.notification.show('Profile updated', 'Your changes have been saved.', 'success');
        },
        error: () => {
          this.savingProfile.set(false);
          this.notification.show('Update failed', 'Could not save your profile changes.', 'error');
        },
      });
  }

  protected savePassword(): void {
    const currentUser = this.user();
    if (!currentUser || this.passwordForm.invalid || this.savingPassword()) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    this.savingPassword.set(true);
    const { password, confirmPassword } = this.passwordForm.getRawValue();

    this.userService.changeOwnPassword({ id: currentUser.id, password, confirmPassword }).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.passwordForm.reset();
        this.notification.show('Password changed', 'Your password has been updated.', 'success');
      },
      error: () => {
        this.savingPassword.set(false);
        this.notification.show('Change failed', 'Could not update your password.', 'error');
      },
    });
  }
}
