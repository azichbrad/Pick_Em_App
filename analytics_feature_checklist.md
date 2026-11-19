# Pick'em App: Analytics & PRO Paywall Checklist

## Phase 1: Database & Entities (The Foundation)
- [x] Add a `boolean isPremium` (default `false`) column to the `Player` entity and PostgreSQL table to control access.
- [x] Create a new `GameAnalytics` entity that maps One-to-One with the `Game` entity.
- [x] Add advanced metric fields to the `GameAnalytics` entity: `awayEpa`, `homeEpa`, `awaySuccessRate`, `homeSuccessRate`, `awayHavoc`, and `aiStoryline`.

## Phase 2: Data Pipelines (The Engines)
- [x] Build a Spring Boot REST client for the free CollegeFootballData (CFBD) API to pull ATS records, Havoc, and Finishing Drives.
- [x] Write a lightweight Python script using the `nflverse` (`nfl-data-py`) library to fetch weekly NFL EPA and success rates.
- [x] Set up the Python script to run on the Raspberry Pi and push a JSON payload to a secure endpoint on the Spring Boot app.
- [ ] Set up a method to send raw game stats and ESPN injury data to the Gemini API to generate a 2-sentence "Game Vibe & Storyline".

## Phase 3: Service Layer & Scheduling
- [x] Create an `AnalyticsService` that takes the raw data from CFBD, the NFL payload, and Gemini, normalizes it, and saves it to the `GameAnalytics` table.
- [x] Add a new `@Scheduled` cron job set to run Thursday mornings to ensure all analytics are generated before the weekend's games start.

## Phase 4: Vaadin UI (The Paywall & Visuals)
- [ ] Add a "📊 Analytics" button or icon next to each matchup on the existing weekly board view.
- [ ] Create an `AnalyticsDialog` class extending Vaadin's `Dialog` component to house the pop-up UI.
- [ ] Implement the Freemium Gatekeeper logic: 
  - [ ] If `player.isPremium()` is `true`, render the actual stats and storylines.
  - [ ] If `false`, render a teaser view with blurred-out text, a locked icon, and an "Unlock PRO Analytics" button.
- [ ] Recreate the Sasser-style visualization sliders using Vaadin's `ProgressBar` components or custom HTML/CSS to show team advantages.

## Phase 5: Monetization 
- [ ] **MVP:** Have the "Unlock PRO" button display a Venmo QR code (manually flip their `isPremium` flag to `true` in the database when they pay).
- [ ] **V2:** Swap the Venmo code for a Stripe Checkout link that uses webhooks to automatically update the player's premium status.