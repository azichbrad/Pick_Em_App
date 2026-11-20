package com.pickem.app.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.pickem.app.dto.TeamAtsSplitDTO;
import com.pickem.app.model.AtsSituation;
import com.pickem.app.model.Game;
import com.pickem.app.repository.GameRepository;
import com.pickem.app.service.game.GameAnalyticsService;
import com.pickem.app.service.game.TeamAtsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CfbdSyncService {

    private final GameAnalyticsService analyticsService;
    private final GameRepository gameRepo;
    private final TeamAtsService teamAtsService;
    private final RestTemplate restTemplate;

    @Value("${cfbd.api.key}")
    private String apiKey;

    private static final List<String> PREFERRED_PROVIDERS = List.of(
            "consensus", "DraftKings", "ESPN Bet", "Bovada"
    );

    public CfbdSyncService(GameAnalyticsService analyticsService,
                           GameRepository gameRepo,
                           TeamAtsService teamAtsService) {
        this.analyticsService = analyticsService;
        this.gameRepo = gameRepo;
        this.teamAtsService = teamAtsService;
        this.restTemplate = new RestTemplate();
    }

    private HttpEntity<String> authEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Accept", "application/json");
        return new HttpEntity<>(headers);
    }

    /**
     * Fetches cumulative season averages for all teams and applies them to upcoming NCAAF games,
     * then refreshes season-long situational ATS splits.
     */
    public void syncSeasonAdvancedStats(int year) {
        System.out.println("Fetching season-long advanced stats from CFBD for year: " + year);
        String url = "https://api.collegefootballdata.com/stats/season/advanced?year=" + year;

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, authEntity(), JsonNode.class);
            JsonNode rootArray = response.getBody();

            if (rootArray != null && rootArray.isArray()) {
                Map<String, JsonNode> teamStatsMap = new HashMap<>();
                for (JsonNode teamNode : rootArray) {
                    teamStatsMap.put(teamNode.path("team").asText(), teamNode);
                }

                List<Game> upcomingNcaafGames = gameRepo.findAll().stream()
                        .filter(g -> "NCAAF".equals(g.getSport()) && !g.getCompleted())
                        .toList();

                int updatedCount = 0;
                for (Game game : upcomingNcaafGames) {
                    JsonNode awayStats = teamStatsMap.get(game.getAwayTeam());
                    JsonNode homeStats = teamStatsMap.get(game.getHomeTeam());

                    if (awayStats != null && homeStats != null) {
                        analyticsService.updateGameMetrics(
                                game.getId(),
                                awayStats.path("offense").path("ppa").asDouble(),
                                homeStats.path("offense").path("ppa").asDouble(),
                                awayStats.path("offense").path("successRate").asDouble(),
                                homeStats.path("offense").path("successRate").asDouble(),
                                awayStats.path("defense").path("havoc").path("total").asDouble(),
                                homeStats.path("defense").path("havoc").path("total").asDouble()
                        );
                        updatedCount++;
                    }
                }
                System.out.println("Successfully updated season averages for " + updatedCount + " upcoming NCAAF games.");
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch CFBD season stats: " + e.getMessage());
        }

        // Keep NCAAF situational ATS splits in sync alongside advanced stats
        syncSeasonAtsSplits(year);
    }

    /**
     * Fetches completed FBS regular-season games and closing spreads from CFBD,
     * computes all 14 situational ATS splits, and bulk-upserts them into team_ats_splits.
     */
    public int syncSeasonAtsSplits(int year) {
        System.out.println("Fetching CFBD games & betting lines for " + year + " ATS splits...");
        String gamesUrl = "https://api.collegefootballdata.com/games?year=" + year + "&seasonType=regular&classification=fbs";
        String linesUrl = "https://api.collegefootballdata.com/lines?year=" + year + "&seasonType=regular";

        try {
            JsonNode gamesNode = restTemplate.exchange(gamesUrl, HttpMethod.GET, authEntity(), JsonNode.class).getBody();
            JsonNode linesNode = restTemplate.exchange(linesUrl, HttpMethod.GET, authEntity(), JsonNode.class).getBody();

            if (gamesNode == null || !gamesNode.isArray() || linesNode == null || !linesNode.isArray()) {
                System.err.println("CFBD games or lines response was empty.");
                return 0;
            }

            // 1. Index closing spreads by CFBD game ID
            Map<Long, Double> spreadByGameId = new HashMap<>();
            for (JsonNode gameLine : linesNode) {
                long gameId = gameLine.path("id").asLong(-1);
                if (gameId <= 0) continue;
                Double spread = extractClosingSpread(gameLine.path("lines"));
                if (spread != null) {
                    spreadByGameId.put(gameId, spread);
                }
            }

            // 2. Collect completed games sorted chronologically
            List<JsonNode> completedGames = new ArrayList<>();
            Set<String> fbsTeams = new HashSet<>();

            for (JsonNode g : gamesNode) {
                String home = g.path("homeTeam").asText("").trim();
                String away = g.path("awayTeam").asText("").trim();
                if ("fbs".equalsIgnoreCase(g.path("homeClassification").asText(""))) fbsTeams.add(home);
                if ("fbs".equalsIgnoreCase(g.path("awayClassification").asText(""))) fbsTeams.add(away);

                if (!home.isEmpty() && !away.isEmpty()
                        && !g.path("homePoints").isMissingNode() && !g.path("homePoints").isNull()
                        && !g.path("awayPoints").isMissingNode() && !g.path("awayPoints").isNull()) {
                    completedGames.add(g);
                }
            }

            completedGames.sort(Comparator
                    .comparingInt((JsonNode g) -> g.path("week").asInt(0))
                    .thenComparing(g -> g.path("startDate").asText("")));

            // 3. Accumulate splits per (team, situation)
            Map<String, SplitBucket> buckets = new HashMap<>();
            Map<String, PriorGame> lastGame = new HashMap<>();

            for (JsonNode g : completedGames) {
                long gameId = g.path("id").asLong(-1);
                String home = g.path("homeTeam").asText("").trim();
                String away = g.path("awayTeam").asText("").trim();
                int week = g.path("week").asInt(0);
                LocalDate gameDate = parseDate(g.path("startDate").asText(""));
                int homePts = g.path("homePoints").asInt();
                int awayPts = g.path("awayPoints").asInt();
                int scoreDiff = homePts - awayPts;

                boolean isNeutral = g.path("neutralSite").asBoolean(false);
                boolean isConfGame = g.path("conferenceGame").asBoolean(false);

                Double spread = spreadByGameId.get(gameId);

                // Compute rest days from prior completed game (if both teams played previously)
                PriorGame homePrev = lastGame.get(home);
                PriorGame awayPrev = lastGame.get(away);
                Long homeRest = (homePrev != null && homePrev.date != null && gameDate != null)
                        ? ChronoUnit.DAYS.between(homePrev.date, gameDate) : null;
                Long awayRest = (awayPrev != null && awayPrev.date != null && gameDate != null)
                        ? ChronoUnit.DAYS.between(awayPrev.date, gameDate) : null;

                // Only record ATS splits if a valid closing spread exists
                if (spread != null) {
                    // CFBD convention: negative spread = home favored
                    double homeMargin = scoreDiff + spread;
                    double awayMargin = -homeMargin;

                    recordTeamGame(buckets, fbsTeams, home, true, homeMargin, spread,
                            isNeutral, isConfGame, week, homePrev, homeRest, awayRest);
                    recordTeamGame(buckets, fbsTeams, away, false, awayMargin, spread,
                            isNeutral, isConfGame, week, awayPrev, awayRest, homeRest);
                }

                // Always update straight-up prior game tracker (even for unlined FCS games)
                String homeOutcome = scoreDiff > 0 ? "W" : (scoreDiff < 0 ? "L" : "T");
                String awayOutcome = scoreDiff < 0 ? "W" : (scoreDiff > 0 ? "L" : "T");
                lastGame.put(home, new PriorGame(week, gameDate, homeOutcome));
                lastGame.put(away, new PriorGame(week, gameDate, awayOutcome));
            }

            // 4. Ensure every FBS team has all 14 situations initialized and build DTOs
            List<TeamAtsSplitDTO> payload = new ArrayList<>();
            for (String team : fbsTeams) {
                for (AtsSituation sit : AtsSituation.values()) {
                    SplitBucket b = buckets.getOrDefault(keyFor(team, sit.name()), new SplitBucket());
                    int decided = b.wins + b.losses;
                    double coverPct = decided > 0 ? Math.round((b.wins * 1000.0) / decided) / 10.0 : 0.0;
                    double atsMargin = b.games > 0 ? Math.round((b.marginSum / b.games) * 100.0) / 100.0 : 0.0;
                    payload.add(new TeamAtsSplitDTO(
                            team, sit.name(), b.wins, b.losses, b.pushes, coverPct, atsMargin
                    ));
                }
            }

            int synced = teamAtsService.upsertSplits("NCAAF", year, payload);
            System.out.println("✅ Synced " + synced + " NCAAF ATS split rows for " + year);
            return synced;

        } catch (Exception e) {
            System.err.println("Failed to sync CFBD ATS splits: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    private void recordTeamGame(Map<String, SplitBucket> buckets,
                                Set<String> fbsTeams,
                                String team,
                                boolean isHome,
                                double margin,
                                double homeSpread,
                                boolean isNeutral,
                                boolean isConfGame,
                                int week,
                                PriorGame prev,
                                Long teamRest,
                                Long oppRest) {
        if (!fbsTeams.isEmpty() && !fbsTeams.contains(team)) {
            return;
        }

        List<String> applicable = new ArrayList<>();
        applicable.add("ALL_GAMES");

        if (!isNeutral) {
            applicable.add(isHome ? "HOME" : "AWAY");
        }

        // Negative homeSpread = home favored; positive homeSpread = away favored
        boolean isFav = isHome ? (homeSpread < 0) : (homeSpread > 0);
        boolean isDog = isHome ? (homeSpread > 0) : (homeSpread < 0);

        if (isFav) {
            applicable.add("FAVORITE");
            if (!isNeutral) applicable.add(isHome ? "HOME_FAV" : "AWAY_FAV");
        } else if (isDog) {
            applicable.add("UNDERDOG");
            if (!isNeutral) applicable.add(isHome ? "HOME_DOG" : "AWAY_DOG");
        }

        if (prev != null) {
            if ("W".equals(prev.outcome)) applicable.add("AFTER_WIN");
            else if ("L".equals(prev.outcome)) applicable.add("AFTER_LOSS");
            if ((week - prev.week) > 1) applicable.add("AFTER_BYE");
        }

        if (week > 1 && teamRest != null && oppRest != null && teamRest > oppRest) {
            applicable.add("REST_ADV");
        }

        if (isConfGame) {
            applicable.add("DIVISION");
        }

        for (String sit : applicable) {
            buckets.computeIfAbsent(keyFor(team, sit), k -> new SplitBucket()).record(margin);
        }
    }

    private Double extractClosingSpread(JsonNode linesArray) {
        if (linesArray == null || !linesArray.isArray() || linesArray.isEmpty()) {
            return null;
        }

        for (String provider : PREFERRED_PROVIDERS) {
            for (JsonNode line : linesArray) {
                if (provider.equalsIgnoreCase(line.path("provider").asText(""))) {
                    Double val = parseDouble(line.path("spread"));
                    if (val != null) return val;
                }
            }
        }

        for (JsonNode line : linesArray) {
            Double val = parseDouble(line.path("spread"));
            if (val != null) return val;
        }
        return null;
    }

    private Double parseDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isNumber()) return node.asDouble();
        String text = node.asText("").trim();
        if (text.isEmpty()) return null;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }

    private String keyFor(String team, String situation) {
        return team + "|" + situation;
    }

    private static class PriorGame {
        final int week;
        final LocalDate date;
        final String outcome;

        PriorGame(int week, LocalDate date, String outcome) {
            this.week = week;
            this.date = date;
            this.outcome = outcome;
        }
    }

    private static class SplitBucket {
        int wins;
        int losses;
        int pushes;
        double marginSum;
        int games;

        void record(double margin) {
            if (margin > 0) wins++;
            else if (margin < 0) losses++;
            else pushes++;
            marginSum += margin;
            games++;
        }
    }
}