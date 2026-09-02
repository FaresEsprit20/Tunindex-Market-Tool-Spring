import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Sidebar } from '../../shared/components/sidebar/sidebar';
import { Navbar } from '../../shared/components/navbar/navbar';

/**
 * Route-level shell for every authenticated page: a fixed sidebar, a top
 * navbar, and a scrollable content area. Analogous to AuthShell but for the
 * signed-in part of the app.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, Sidebar, Navbar],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShell {}
