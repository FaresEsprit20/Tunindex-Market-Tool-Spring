import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { UserExtendedDto } from '../../../core/models/user.model';
import { User } from '../../../core/services/user';
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
  private readonly notification = inject(Notification);

  protected readonly loading = signal(true);
  protected readonly user = signal<UserExtendedDto | null>(null);
  protected readonly savingProfile = signal(false);
  protected readonly savingPassword = signal(false);

  protected readonly profileForm = this.fb.nonNullable.group({
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
    this.userService.getAuthUser().subscribe({
      next: (u) => {
        this.user.set(u);
        this.profileForm.patchValue({
          numTel: u.numTel,
          address1: u.address?.address1 ?? '',
          address2: u.address?.address2 ?? '',
          city: u.address?.city ?? '',
          zipCode: u.address?.zipCode ?? '',
          country: u.address?.country ?? '',
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected saveProfile(): void {
    const currentUser = this.user();
    if (!currentUser || this.profileForm.invalid || this.savingProfile()) {
      this.profileForm.markAllAsTouched();
      return;
    }

    this.savingProfile.set(true);
    const { numTel, address1, address2, city, zipCode, country } = this.profileForm.getRawValue();

    this.userService
      .updateProfile({
        email: currentUser.email,
        numTel,
        photo: currentUser.photo ?? '',
        address: { address1, address2, city, zipCode, country },
      })
      .subscribe({
        next: () => {
          this.savingProfile.set(false);
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
