import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { PipelinePhase, WorkerActivity } from '../../../core/models/pipeline.model';
import { Pipeline } from '../../../core/services/pipeline';
import { Notification } from '../../../core/services/notification';

const MAX_FETCH_SLOTS = 5;
const MAX_SAVE_SLOTS = 10;

@Component({
  selector: 'app-pipeline-monitor',
  imports: [],
  templateUrl: './pipeline-monitor.html',
  styleUrl: './pipeline-monitor.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PipelineMonitor {
  private readonly pipeline = inject(Pipeline);
  private readonly notification = inject(Notification);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly starting = signal(false);
  protected readonly snapshot = this.pipeline.snapshot;
  protected readonly connected = this.pipeline.connected;

  protected readonly state = computed(() => this.snapshot()?.state ?? 'IDLE');
  protected readonly isRunning = computed(() => this.state() === 'RUNNING');
  protected readonly hasRunBefore = computed(() => this.state() !== 'IDLE');

  protected readonly total = computed(() => this.snapshot()?.totalStocks ?? 0);
  protected readonly completedCount = computed(() => this.snapshot()?.completedCount ?? 0);
  protected readonly failedCount = computed(() => this.snapshot()?.failedCount ?? 0);
  protected readonly throughput = computed(() => this.snapshot()?.throughputPerSec ?? 0);
  protected readonly elapsedMs = computed(() => this.snapshot()?.elapsedMs ?? 0);

  protected readonly progressPct = computed(() => {
    const total = this.total();
    if (total === 0) return 0;
    return Math.min(100, Math.round(((this.completedCount() + this.failedCount()) / total) * 100));
  });

  protected readonly ringOffset = computed(() => {
    const circumference = 2 * Math.PI * 52;
    return circumference - (this.progressPct() / 100) * circumference;
  });

  private readonly activeWorkers = computed(() => this.snapshot()?.activeWorkers ?? []);

  protected readonly fetchWorkers = computed<WorkerActivity[]>(() =>
    this.activeWorkers().filter((w) => w.phase === 'FETCHING' || w.phase === 'ENRICHING'),
  );
  protected readonly saveWorkers = computed<WorkerActivity[]>(() =>
    this.activeWorkers().filter((w) => w.phase === 'SAVING'),
  );

  protected readonly idleFetchSlots = computed(() =>
    Array.from({ length: Math.max(0, MAX_FETCH_SLOTS - this.fetchWorkers().length) }),
  );
  protected readonly idleSaveSlots = computed(() =>
    Array.from({ length: Math.max(0, MAX_SAVE_SLOTS - this.saveWorkers().length) }),
  );

  protected readonly recentEvents = computed(() => this.snapshot()?.recentEvents ?? []);

  constructor() {
    this.pipeline.connect();
    this.destroyRef.onDestroy(() => this.pipeline.disconnect());
  }

  protected triggerStart(): void {
    if (this.starting() || this.isRunning()) {
      return;
    }
    this.starting.set(true);

    this.pipeline.start().subscribe({
      next: (res) => {
        this.starting.set(false);
        if (res.started) {
          this.notification.show('Pipeline started', `Scraping ${res.totalStocks ?? 'all'} tracked stocks…`, 'success');
        } else {
          this.notification.show('Already running', res.message ?? 'A pipeline run is already in progress.', 'info');
        }
      },
      error: () => {
        this.starting.set(false);
        this.notification.show('Could not start pipeline', 'The collector may be unreachable. Try again shortly.', 'error');
      },
    });
  }

  protected elapsedLabel(): string {
    const ms = this.elapsedMs();
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  protected phaseLabel(phase: PipelinePhase): string {
    switch (phase) {
      case 'FETCHING':
        return 'Fetching';
      case 'ENRICHING':
        return 'Enriching';
      case 'SAVING':
        return 'Saving';
    }
  }

  protected timeAgo(iso: string): string {
    const diffMs = Date.now() - new Date(iso).getTime();
    const seconds = Math.max(0, Math.floor(diffMs / 1000));
    if (seconds < 2) return 'just now';
    if (seconds < 60) return `${seconds}s ago`;
    const minutes = Math.floor(seconds / 60);
    return `${minutes}m ago`;
  }
}
