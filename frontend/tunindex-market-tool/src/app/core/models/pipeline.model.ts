export type PipelineState = 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED';
export type PipelinePhase = 'FETCHING' | 'ENRICHING' | 'SAVING';

export interface WorkerActivity {
  threadName: string;
  symbol: string;
  phase: PipelinePhase;
  elapsedMs: number;
}

export interface RecentEvent {
  symbol: string;
  phase: PipelinePhase;
  success: boolean;
  durationMs: number;
  timestamp: string;
}

export interface PipelineSnapshot {
  state: PipelineState;
  totalStocks: number;
  completedCount: number;
  failedCount: number;
  activeWorkerCount: number;
  maxFetchWorkers: number;
  maxSaveWorkers: number;
  startedAt: string | null;
  finishedAt: string | null;
  elapsedMs: number;
  throughputPerSec: number;
  activeWorkers: WorkerActivity[];
  recentEvents: RecentEvent[];
}
