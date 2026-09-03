import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AlertRule, AlertTypeKey, AlertTypeOption, THRESHOLD_UNITS } from '../../../core/models/alert.model';
import { Alerts } from '../../../core/services/alerts';
import { Notification } from '../../../core/services/notification';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';

/**
 * Alert rules: what the user is watching for, and the switchboard for it.
 * Rules are evaluated server-side on a timer against the same figures the
 * rest of the app displays — see AlertEvaluationService.
 */
@Component({
  selector: 'app-alerts-page',
  imports: [DatePipe, EmptyState, SkeletonBlock],
  templateUrl: './alerts-page.html',
  styleUrl: './alerts-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlertsPage {
  private readonly alerts = inject(Alerts);
  private readonly notification = inject(Notification);

  protected readonly thresholdUnits = THRESHOLD_UNITS;

  protected readonly loading = signal(true);
  protected readonly rules = signal<AlertRule[]>([]);
  protected readonly types = signal<AlertTypeOption[]>([]);
  protected readonly saving = signal(false);

  // New-rule form
  protected readonly formSymbol = signal('');
  protected readonly formType = signal<AlertTypeKey>('PRICE_ABOVE');
  protected readonly formThreshold = signal('');

  protected readonly selectedType = computed(() =>
    this.types().find((t) => t.type === this.formType()),
  );
  protected readonly needsThreshold = computed(() => this.selectedType()?.requiresThreshold ?? true);
  protected readonly activeCount = computed(() => this.rules().filter((r) => r.enabled).length);

  protected readonly canSubmit = computed(() => {
    if (!this.formSymbol().trim()) return false;
    if (this.needsThreshold() && !this.formThreshold().trim()) return false;
    return !this.saving();
  });

  constructor() {
    this.alerts.listTypes().subscribe({
      next: (types) => this.types.set(types),
      error: () => undefined,
    });
    this.loadRules();
  }

  private loadRules(): void {
    this.loading.set(true);
    this.alerts.listRules().subscribe({
      next: (rules) => {
        this.rules.set(rules);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected create(): void {
    if (!this.canSubmit()) return;

    this.saving.set(true);
    const threshold = this.needsThreshold() ? Number(this.formThreshold()) : null;

    this.alerts
      .createRule({
        symbol: this.formSymbol().trim().toUpperCase(),
        type: this.formType(),
        threshold,
      })
      .subscribe({
        next: (rule) => {
          this.rules.update((rules) => [rule, ...rules]);
          this.formSymbol.set('');
          this.formThreshold.set('');
          this.saving.set(false);
          this.notification.show('Alert created', `Watching ${rule.symbol}.`, 'success');
        },
        error: (err) => {
          this.saving.set(false);
          const message = err?.error?.errors?.[0] ?? err?.error?.message ?? 'Please check the values and try again.';
          this.notification.show("Couldn't create alert", message, 'error');
        },
      });
  }

  protected toggle(rule: AlertRule): void {
    this.alerts.toggleRule(rule.id).subscribe({
      next: (updated) => {
        this.rules.update((rules) => rules.map((r) => (r.id === updated.id ? updated : r)));
      },
      error: () => this.notification.show("Couldn't update alert", 'Please try again.', 'error'),
    });
  }

  protected remove(rule: AlertRule): void {
    this.alerts.deleteRule(rule.id).subscribe({
      next: () => {
        this.rules.update((rules) => rules.filter((r) => r.id !== rule.id));
        this.notification.show('Alert deleted', `No longer watching ${rule.symbol}.`, 'success');
      },
      error: () => this.notification.show("Couldn't delete alert", 'Please try again.', 'error'),
    });
  }

  protected unitFor(type: AlertTypeKey): string {
    return this.thresholdUnits[type] ?? '';
  }
}
