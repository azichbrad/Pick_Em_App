package com.pickem.app.service.game;

import com.pickem.app.model.Game;
import com.pickem.app.model.GameAnalytics;
import com.pickem.app.repository.GameAnalyticsRepository;
import com.pickem.app.repository.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GameAnalyticsService {

    private final GameAnalyticsRepository analyticsRepo;
    private final GameRepository gameRepo;

    public GameAnalyticsService(GameAnalyticsRepository analyticsRepo, GameRepository gameRepo) {
        this.analyticsRepo = analyticsRepo;
        this.gameRepo = gameRepo;
    }

    /**
     * Initializes an empty analytics record for a game if one doesn't exist yet.
     */
    @Transactional
    public GameAnalytics getOrCreateAnalytics(String gameId) {
        return analyticsRepo.findByGameId(gameId).orElseGet(() -> {
            Game game = gameRepo.findById(gameId)
                    .orElseThrow(() -> new IllegalArgumentException("Game not found: " + gameId));

            GameAnalytics newAnalytics = new GameAnalytics();
            newAnalytics.setGame(game);
            return analyticsRepo.save(newAnalytics);
        });
    }

    /**
     * Updates the core metrics for a specific game.
     */
    @Transactional
    public void updateGameMetrics(String gameId, Double awayEpa, Double homeEpa,
                                  Double awaySuccessRate, Double homeSuccessRate,
                                  Double awayHavoc, Double homeHavoc) {

        GameAnalytics analytics = getOrCreateAnalytics(gameId);

        analytics.setAwayEpa(awayEpa);
        analytics.setHomeEpa(homeEpa);
        analytics.setAwaySuccessRate(awaySuccessRate);
        analytics.setHomeSuccessRate(homeSuccessRate);
        analytics.setAwayHavoc(awayHavoc);
        analytics.setHomeHavoc(homeHavoc);

        analyticsRepo.save(analytics);
        System.out.println("Updated advanced metrics for Game ID: " + gameId);
    }

    /**
     * Example of updating the dynamic JSONB map.
     */
    @Transactional
    public void addDynamicMetric(String gameId, String metricKey, Object metricValue) {
        GameAnalytics analytics = getOrCreateAnalytics(gameId);
        analytics.getDynamicMetrics().put(metricKey, metricValue);
        analyticsRepo.save(analytics);
    }

    public record TeamStatRankDTO(
            String teamName,
            String logoUrl,
            Double epa,
            Double successRate,
            Double havoc
    ) {}

    public record MatchupAnalyticsDTO(
            String gameId,
            String awayTeam,
            String homeTeam,
            String awayLogo,
            String homeLogo,
            Instant commenceTime,
            Double awaySpread,
            Double homeSpread,
            Double awayEpa,
            Double homeEpa,
            Double awaySuccessRate,
            Double homeSuccessRate,
            Double awayHavoc,
            Double homeHavoc,
            String aiStoryline
    ) {}

    @Transactional(readOnly = true)
    public List<TeamStatRankDTO> getTeamStatsForSport(String sport) {
        Map<String, TeamStatRankDTO> teamMap = new HashMap<>();
        Map<String, Instant> latestSeen = new HashMap<>();

        // Inside getTeamStatsForSport(String sport):
        for (GameAnalytics ga : analyticsRepo.findBySportWithGame(sport)) {
            Game game = ga.getGame();
            if (game == null || game.getSport() == null || !sport.equalsIgnoreCase(game.getSport())) {
                continue;
            }
            Instant gameTime = game.getCommenceTime() != null ? game.getCommenceTime() : Instant.EPOCH;

            if (game.getAwayTeam() != null && ga.getAwayEpa() != null) {
                String key = game.getAwayTeam().toLowerCase();
                if (!latestSeen.containsKey(key) || gameTime.isAfter(latestSeen.get(key))) {
                    latestSeen.put(key, gameTime);
                    teamMap.put(key, new TeamStatRankDTO(
                            game.getAwayTeam(),
                            game.getAwayLogo(),
                            ga.getAwayEpa(),
                            ga.getAwaySuccessRate(),
                            ga.getAwayHavoc()
                    ));
                }
            }

            if (game.getHomeTeam() != null && ga.getHomeEpa() != null) {
                String key = game.getHomeTeam().toLowerCase();
                if (!latestSeen.containsKey(key) || gameTime.isAfter(latestSeen.get(key))) {
                    latestSeen.put(key, gameTime);
                    teamMap.put(key, new TeamStatRankDTO(
                            game.getHomeTeam(),
                            game.getHomeLogo(),
                            ga.getHomeEpa(),
                            ga.getHomeSuccessRate(),
                            ga.getHomeHavoc()
                    ));
                }
            }
        }

        return new ArrayList<>(teamMap.values());
    }

    @Transactional(readOnly = true)
    public List<MatchupAnalyticsDTO> getMatchupAnalyticsForGames(List<Game> weeklyGames) {
        if (weeklyGames == null || weeklyGames.isEmpty()) {
            return List.of();
        }

        Set<String> gameIds = weeklyGames.stream()
                .map(Game::getId)
                .collect(Collectors.toSet());

        // Inside getMatchupAnalyticsForGames(List<Game> weeklyGames):
        Map<String, GameAnalytics> analyticsByGameId = analyticsRepo.findByGameIdsWithGame(gameIds).stream()
                .filter(ga -> ga.getGame() != null)
                .collect(Collectors.toMap(ga -> ga.getGame().getId(), ga -> ga, (a, b) -> a));

        return weeklyGames.stream()
                .sorted(Comparator.comparing(
                        g -> g.getCommenceTime() != null ? g.getCommenceTime() : Instant.MAX))
                .map(g -> {
                    GameAnalytics ga = analyticsByGameId.get(g.getId());
                    return new MatchupAnalyticsDTO(
                            g.getId(),
                            g.getAwayTeam(),
                            g.getHomeTeam(),
                            g.getAwayLogo(),
                            g.getHomeLogo(),
                            g.getCommenceTime(),
                            g.getAwaySpread(),
                            g.getHomeSpread(),
                            ga != null ? ga.getAwayEpa() : null,
                            ga != null ? ga.getHomeEpa() : null,
                            ga != null ? ga.getAwaySuccessRate() : null,
                            ga != null ? ga.getHomeSuccessRate() : null,
                            ga != null ? ga.getAwayHavoc() : null,
                            ga != null ? ga.getHomeHavoc() : null,
                            ga != null ? ga.getAiStoryline() : null
                    );
                })
                .toList();
    }
}