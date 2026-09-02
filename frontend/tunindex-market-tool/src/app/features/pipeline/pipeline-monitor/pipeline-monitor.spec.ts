import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PipelineMonitor } from './pipeline-monitor';

// JSDOM (the test environment) doesn't implement EventSource — stub it so
// the component's live-connect-on-construct behavior doesn't throw here.
class FakeEventSource {
  onopen: (() => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  onerror: (() => void) | null = null;
  close(): void {}
}

describe('PipelineMonitor', () => {
  let component: PipelineMonitor;
  let fixture: ComponentFixture<PipelineMonitor>;

  beforeEach(async () => {
    (globalThis as unknown as { EventSource: unknown }).EventSource = FakeEventSource;

    await TestBed.configureTestingModule({
      imports: [PipelineMonitor],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(PipelineMonitor);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
