import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MarketSummary } from '../market-summary/market-summary';
import { SectorDistribution } from '../sector-distribution/sector-distribution';
import { OwnershipDistribution } from '../ownership-distribution/ownership-distribution';

@Component({
  selector: 'app-dashboard',
  imports: [MarketSummary, SectorDistribution, OwnershipDistribution],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {}
