import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MarketSummary } from '../market-summary/market-summary';
import { SectorDistribution } from '../sector-distribution/sector-distribution';
import { OwnershipDistribution } from '../ownership-distribution/ownership-distribution';
import { TopMovers } from '../top-movers/top-movers';
import { MarketPulse } from '../market-pulse/market-pulse';
import { MarketNews } from '../market-news/market-news';
import { MarketStatus } from '../../market/market-status/market-status';
import { TopOpportunities } from '../top-opportunities/top-opportunities';
import { MacroPanel } from '../../../shared/components/macro-panel/macro-panel';
import { CommoditiesBanner } from '../../../shared/components/commodities-banner/commodities-banner';
import { AdSlot } from '../../../shared/components/ad-slot/ad-slot';

@Component({
  selector: 'app-dashboard',
  imports: [
    MarketSummary,
    SectorDistribution,
    OwnershipDistribution,
    TopMovers,
    MarketPulse,
    MarketNews,
    MarketStatus,
    TopOpportunities,
    MacroPanel,
    CommoditiesBanner,
    AdSlot,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {}
