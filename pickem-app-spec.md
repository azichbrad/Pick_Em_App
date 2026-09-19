# Sports Pick'Em Webapp — Specification

## 1. Overview
A webapp for a small group of friends (5 players) to make weekly picks against the spread (ATS) on college football (NCAAF) and NFL games. Odds come from SharpAPI. Each player has 5 picks per week per sport (10 total per week), displayed in their own personal column. Picks lock at kickoff of the selected game, and results are auto-graded once final scores are available.

**Note**: the frontend UI already exists and is being built on top of. This spec describes the required data, backend logic, and behavior to implement/wire into that existing UI — it is not a request to design a new interface from scratch.

## 2. Sports Coverage
- **NCAAF**: All Division I teams — both FBS and FCS (confirmed). Note: FCS games may have limited or no odds coverage from SharpAPI; the game list/board should still display FCS matchups even if some or all odds fields come back empty for them.
- **NFL**: All 32 teams.
- Each sport is organized by week (Week 1, Week 2, Week 3, etc.), matching that sport's official season week numbering.

## 3. Data Source — SharpAPI
- Base domain: sharpapi.io *(confirm exact base URL, auth method, and response schema against SharpAPI's actual docs before building — do not assume field names)*.
- Rate limit: 12 requests/minute.
- **Unresolved**: what counts as "1 request" is still being confirmed against SharpAPI's actual documentation (per game? per market? per sport call?). Do not hardcode a polling interval until this is confirmed — the formula in Section 5 depends on it.
- Data needed per game:
  - Point spread (both teams)
  - Game total (over/under)
  - Team totals (each team's individual implied total)
- Rough estimate (unconfirmed): 4–6 requests per NCAAF game for spread + total + team totals; similar for NFL.

## 4. Odds Formatting
Raw odds from the API may come back as decimal odds. The app must convert these to standard American sportsbook format for display.

- **Spread display**: `[Team] [+/-Spread] [American Price]` → e.g. `Clemson +10.5 -110`
- **Total display**: `Over/Under [Number] [American Price]` → e.g. `Over 54.5 -110`
- **Team total display**: `[Team] Over/Under [Number] [American Price]` → e.g. `Clemson Over 27.5 -115`

**Decimal-to-American conversion:**
- If decimal odds ≥ 2.00: `American = (decimal − 1) × 100`
- If decimal odds < 2.00: `American = −100 / (decimal − 1)`

## 5. Odds Refresh & Caching Strategy
- Do **not** call SharpAPI live on every user page load/click — with only 5 users, the bottleneck is SharpAPI's rate limit, not app traffic.
- Run a scheduled background job that pulls odds for all active games (NCAAF + NFL, current week) on a fixed interval and writes results to a cache (JSON file or lightweight DB table).
- Frontend and pick logic always read from this cache, never directly from SharpAPI.
- Interval should be calculated once the true "requests per pull" cost is confirmed (Section 3): `interval_minutes = ceil(total_requests_needed_per_full_pull / 12)`.
- Optional enhancement (flag for later): tighten refresh interval as kickoff approaches for a given game, since lines move more near game time.

## 6. Default Landing / Current Week Detection
- Opening the NCAAF page defaults to the current NCAAF week.
- Navigating to the NFL page defaults to the current NFL week (independent of NCAAF's week number, since the two seasons don't align).
- **Week calculation method (confirmed approach)**: SharpAPI returns each game's kickoff as `event_start_time` (e.g. `"2026-09-03T22:00Z"`). Weeks are defined as Tuesday-through-Monday windows. Any game whose `event_start_time` falls within a given Tuesday–Monday window belongs to that week.
  - Example: Week 1 window = Tuesday, September 1, 2026 through Monday, September 7, 2026. Any game with an `event_start_time` in that range is Week 1. Week 2 begins the following Tuesday, and so on.
  - **Confirmed**: the source conversation initially listed the Week 1 window with a typo ("Tuesday, November 1, 2026 through Monday, September 7, 2026" — out of order, and Nov 1, 2026 is a Sunday, not a Tuesday). This has been corrected and confirmed as **Sept 1–7, 2026**.
  - "Current week" = whichever Tuesday–Monday window contains today's date.
  - This logic should be identical for NFL, using the NFL's own Week 1 start Tuesday (confirm NFL's actual Week 1 date range separately, since NFL and NCAAF seasons don't start on the same date).

## 7. Game Board / Odds List UI
- Displays all games for the currently selected week, grouped as one list for that week (not pre-split into sections).
- Sortable/filterable by conference (Big Ten, SEC, ACC, Big 12, etc.).
- Includes a search bar to filter by team name.
- Each game row shows: matchup, kickoff date/time, spread (both teams), total, and team totals (both teams).
- **Game availability state**: every game for the week always appears in this list — none are hidden — but each game's selectability depends on its status relative to `event_start_time`:
  - **Upcoming** (current time is before `event_start_time`): fully selectable, normal display.
  - **Live or Finished** (current time is at or after `event_start_time`): displayed greyed out / visually disabled and cannot be selected, regardless of whether that specific game was ever picked by anyone.
  - This applies every time the modal is opened — e.g., a player filling their 5th slot late on Saturday should see Thursday/Friday games and any already-kicked-off Saturday games (noon window, etc.) greyed out, while Saturday evening/night and Sunday games remain selectable. This prevents someone from selecting a finished game's closing line after the outcome is already known.
  - This check is separate from the per-slot lock in Section 9 — it applies to the entire game list, not just to a slot the player has already filled.

## 8. Player Picks UI
- Each of the 5 players has their own container/column, visible side by side.
- Each container has 5 empty slots for NCAAF and a separate 5 empty slots for NFL, each week (confirmed: 10 total picks per player per week — 5 college + 5 NFL, not a shared pool).
- Clicking an empty slot opens the game list (Section 7) for that week.
- From the list, the player selects **one** line for that slot: a spread pick (a specific team), a total pick (over or under), or a team total pick (a specific team, over or under).
- Once selected, the pick (team/side + line + price) displays under that player's name in that slot.

## 9. Pick Rules
- At any given moment, a player's 5 active slots (per sport) must all reference different games — the same game cannot occupy two slots **at the same time**. (This does not restrict what a player can do within a single slot over time — see below.)
- A player can only select games that are still **Upcoming** (see Section 7's availability state) — any game that is live or finished is not selectable for any slot, including a previously-empty slot being filled late in the week.
- Before kickoff of the selected game, a player **can** change or replace the pick in a slot as many times as they want.
- **Reselection restriction ("burned selections")**: a specific selection is the combination of (game + market type + specific team/side) — e.g., "Clemson +10.5 ATS" is a distinct selection from "LSU −10.5 ATS" on that same game, and both are distinct from "Clemson team total" or the game's total. Once a player has held a specific selection in a slot and then replaces it with something else, that **exact selection is burned for that player for the rest of the current week** — it cannot be picked again in any slot, even if the number has since moved in their favor. This prevents a player from picking Clemson +10.5 on Wednesday, watching the line move to Clemson +14 on Thursday, and re-selecting Clemson ATS on that improved number.
  - This restriction is narrow and does **not** block the player from: picking a totally different game, picking the other side of the same market on the same game (e.g., switching to LSU ATS), or picking a different market on that same game (team total, game total). Only the exact previously-held selection is off-limits.
  - Burned selections reset at the start of each new week.
- Once the selected game's actual kickoff time passes, that slot locks permanently for the week — no edits, no replacement, regardless of what happens with other slots.
- When a game is graded (see Section 10), the app must record the exact line and price that was showing **at the time the player made the pick**, since the odds shown in the cache will keep moving after that. Grading is based on this locked-in snapshot value, not the closing line (confirmed).

## 10. Grading Engine
- After a game finishes, the app pulls the final score (from SharpAPI or another scores source).
- Grades each pick type as WIN, LOSS, or PUSH:
  - **Spread**: Apply the picked team's spread (from their locked-in snapshot) to the final score margin.
    - Example: Player picks Clemson +10.5. Final score Clemson 24 – LSU 34 (Clemson loses by 10). Since 10 < 10.5, Clemson "covers" → graded **WIN**.
  - **Total**: Compare actual combined final score to the picked over/under number.
  - **Team total**: Compare the specific team's actual final score to the picked over/under number for that team.
  - **Push**: If the actual result lands exactly on the number (e.g., picked +10, lost by exactly 10), grade as **PUSH**, not win or loss.
- Each slot's result should be stored and displayed next to the pick once grading completes.

## 11. Data to Store
- **Games**: id, sport, week, matchup, kickoff datetime, conference(s), current cached odds (spread/total/team totals), final score (once available).
- **Picks**: player, sport, week, slot number, game_id, pick type (spread/total/team_total), selected side/team, line value at pick time, price at pick time, timestamp of pick, locked flag, kickoff time of the picked game, result (win/loss/push/pending).
- **Burned selections** (needed to enforce Section 9's reselection restriction): player, sport, week, game_id, market type, selected side/team, timestamp abandoned. A row is added here whenever a player replaces an active pick with something else. Before saving any new pick, check this table (scoped to the player + sport + current week) to make sure the new (game_id, market type, side) combo isn't already burned.
- **Players**: the 5 names.

## 12. Open Questions / Assumptions Still Needing Confirmation
1. Exact SharpAPI base URL, authentication, and response field structure — pending review of SharpAPI's actual documentation.
2. What SharpAPI counts as "1 request" toward the 12/minute limit — needed to finalize the polling interval math in Section 5. Do not hardcode a refresh interval until this is confirmed.
3. **Week boundary dates**: confirm the exact Tuesday start date for NCAAF Week 1 (Section 6 currently assumes Sept 1–7, 2026, correcting an apparent date typo) and separately confirm the NFL's own Week 1 Tuesday–Monday window, since the two seasons don't start on the same date.
