package com.pickem.app.service;

import com.pickem.app.model.Game;
import com.pickem.app.model.GameAnalytics;
import com.pickem.app.repository.GameAnalyticsRepository;
import com.pickem.app.repository.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
}