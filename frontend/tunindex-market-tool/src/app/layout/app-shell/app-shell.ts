import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Sidebar } from '../../shared/components/sidebar/sidebar';
import { Navbar } from '../../shared/components/navbar/navbar';
import { MarketTicker } from '../../shared/components/market-ticker/market-ticker';
import { CommandPalette } from '../../shared/components/command-palette/command-palette';

/**
 * Route-level shell for every authenticated page: a fixed sidebar, a top
 * navbar, a live ticker strip, and a scrollable content area. Analogous to
 * AuthShell but for the signed-in part of the app — the ticker lives here
 * (not on the pre-login screens) because it's fed by real prices from the
 * stock endpoints, which require a session. The command palette is mounted
 * here too so ⌘K works on every authenticated screen.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, Sidebar, Navbar, MarketTicker, CommandPalette],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShell {}
