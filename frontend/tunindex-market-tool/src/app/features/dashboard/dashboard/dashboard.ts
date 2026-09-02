import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MarketSummary } from '../market-summary/market-summary';
import { SectorDistribution } from '../sector-distribution/sector-distribution';
import { OwnershipDistribution } from '../ownership-distribution/ownership-distribution';
import { TopMovers } from '../top-movers/top-movers';
import { MarketPulse } from '../market-pulse/market-pulse';

@Component({
  selector: 'app-dashboard',
  imports: [MarketSummary, SectorDistribution, OwnershipDistribution, TopMovers, MarketPulse],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {}
