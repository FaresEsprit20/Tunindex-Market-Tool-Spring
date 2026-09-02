import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * The standard bordered surface used for forms, panels and content blocks
 * throughout the app: a plain card with a hairline border and a soft shadow.
 */
@Component({
  selector: 'app-card',
  imports: [],
  templateUrl: './card.html',
  styleUrl: './card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'card-host' },
})
export class Card {}
