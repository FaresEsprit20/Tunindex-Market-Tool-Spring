import { ChangeDetectionStrategy, Component, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { AdGate, GateChallenge } from '../../../core/services/ad-gate';

/**
 * The screen that appears when a feature needs an ad watched first.
 *
 * <p>Mounted once in the shell and driven by {@link AdGate}, so any call the
 * server gates raises it - the page that made the call needs to know nothing
 * about ads.
 *
 * <p>There is no skip control, and that is not an oversight: the server will
 * not issue a grant before the ad's own duration has passed, so a skip button
 * could only lie. The countdown shows the real remaining time rather than a
 * control that would do nothing.
 *
 * <p>Giving up is still possible, and deliberately so. Someone who does not
 * want the feature must be able to get back to the app - a modal with no exit
 * is a broken tab, not a business model. Cancelling simply leaves the feature
 * shut, which the original request already reported.
 */
@Component({
  selector: 'app-ad-gate-overlay',
  imports: [],
  templateUrl: './ad-gate-overlay.html',
  styleUrl: './ad-gate-overlay.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdGateOverlay implements OnDestroy {
  private readonly gate = inject(AdGate);

  protected readonly challenge = signal<GateChallenge | null>(null);
  protected readonly elapsed = signal(0);
  protected readonly claiming = signal(false);
  protected readonly failed = signal(false);

  private timer?: ReturnType<typeof setInterval>;

  /**
   * How long is left, from the client's own count.
   *
   * <p>Only ever a display. The decision belongs to the server, which is why
   * reaching zero triggers a request rather than opening anything - if this
   * counter is fast, the claim is simply refused and the countdown continues.
   */
  protected readonly remaining = computed(() => {
    const current = this.challenge();
    if (!current) {
      return 0;
    }
    return Math.max(0, current.requiredSeconds - this.elapsed());
  });

  protected readonly progress = computed(() => {
    const current = this.challenge();
    if (!current || current.requiredSeconds <= 0) {
      return 0;
    }
    return Math.min(100, (this.elapsed() / current.requiredSeconds) * 100);
  });

  /**
   * True from asking for an ad until one arrives.
   *
   * <p>Without it the effect can start a second view while the first request
   * is still in flight: `challenge()` is not set until the response lands, so
   * any re-run in that window looks like "no ad open yet". That would begin
   * two server-side view sessions and record two impressions for one ad -
   * charging an advertiser twice for something shown once.
   */
  private opening = false;

  constructor() {
    effect(() => {
      const feature = this.gate.current();
      if (feature && !this.challenge() && !this.opening) {
        this.open(feature);
      }
    });
  }

  private open(feature: Parameters<AdGate['start']>[0]): void {
    this.opening = true;
    this.failed.set(false);
    this.elapsed.set(0);
    this.gate.start(feature).subscribe((challenge) => {
      this.opening = false;
      if (!challenge) {
        // Nothing to play. Treated as open rather than shut: no inventory is
        // our problem, not the user's, and locking the feature would cost a
        // user while earning nothing.
        this.gate.settle(feature, true);
        return;
      }
      this.challenge.set(challenge);
      this.startPlayback(challenge);
    });
  }

  private startPlayback(challenge: GateChallenge): void {
    this.stopTimer();
    this.timer = setInterval(() => {
      const next = this.elapsed() + 1;
      this.elapsed.set(next);

      if (next % challenge.heartbeatSeconds === 0) {
        this.gate.heartbeat(challenge.viewToken);
      }
      if (next >= challenge.requiredSeconds) {
        this.stopTimer();
        this.claim(challenge);
      }
    }, 1000);
  }

  private claim(challenge: GateChallenge): void {
    this.claiming.set(true);
    this.gate.complete(challenge.viewToken, challenge.feature).subscribe((granted) => {
      this.claiming.set(false);
      if (granted) {
        this.close(challenge.feature, true);
        return;
      }
      // Refused. Almost always the client counter running a little ahead of
      // the server's, so the honest response is to wait and try again rather
      // than make the user sit through the whole ad a second time.
      this.failed.set(true);
      setTimeout(() => {
        this.failed.set(false);
        this.claim(challenge);
      }, 2000);
    });
  }

  protected cancel(): void {
    const current = this.challenge();
    if (current) {
      this.close(current.feature, false);
    }
  }

  private close(feature: Parameters<AdGate['start']>[0], opened: boolean): void {
    this.stopTimer();
    this.challenge.set(null);
    this.elapsed.set(0);
    this.gate.settle(feature, opened);
  }

  private stopTimer(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }

  ngOnDestroy(): void {
    this.stopTimer();
  }
}
