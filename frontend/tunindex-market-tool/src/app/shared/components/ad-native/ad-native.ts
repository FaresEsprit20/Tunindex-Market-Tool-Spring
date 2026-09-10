import { ChangeDetectionStrategy, Component, OnInit, inject, input } from '@angular/core';
import { Ads } from '../../../core/services/ads';
import { Advertisement } from '../../../core/models/ad.model';

/**
 * An ad styled to sit alongside the surrounding content.
 *
 * <p>Native placements borrow the page's own typography, which is exactly why
 * the disclosure here is more prominent than on a banner rather than less: a
 * unit that reads as editorial and turns out to be paid is the one that
 * actually damages trust. The label is part of the layout, not an overlay that
 * can be missed.
 */
@Component({
  selector: 'app-ad-native',
  imports: [],
  templateUrl: './ad-native.html',
  styleUrl: './ad-native.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdNative implements OnInit {
  private readonly ads = inject(Ads);

  readonly ad = input.required<Advertisement>();

  ngOnInit(): void {
    // No creative to wait on, unlike a banner, so the impression is recorded
    // when the block is rendered.
    this.ads.record(this.ad().id, 'IMPRESSION');
  }

  protected onClick(): void {
    this.ads.record(this.ad().id, 'CLICK');
  }
}
