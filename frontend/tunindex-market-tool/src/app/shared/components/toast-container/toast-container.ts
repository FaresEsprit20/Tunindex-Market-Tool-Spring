import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Notification } from '../../../core/services/notification';

/**
 * Mounted once at the app root. Renders live toasts from the Notification
 * service; each one slides up and fades in on arrival.
 */
@Component({
  selector: 'app-toast-container',
  imports: [],
  templateUrl: './toast-container.html',
  styleUrl: './toast-container.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'toast-container-host' },
})
export class ToastContainer {
  protected readonly notification = inject(Notification);
}
