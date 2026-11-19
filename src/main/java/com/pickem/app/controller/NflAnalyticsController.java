package com.pickem.app.controller;

import com.pickem.app.dto.NflTeamMetricsDTO;
import com.pickem.app.model.Game;
import com.pickem.app.repository.GameRepository;
import com.pickem.app.service.game.GameAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics/nfl")
public class NflAnalyticsController {

    private final GameRepository gameRepo;
    private final GameAnalyticsService analyticsService;

    public NflAnalyticsController(GameRepository gameRepo, GameAnalyticsService analyticsService) {
        this.gameRepo = gameRepo;
        this.analyticsService = analyticsService;
    }

    @PostMapping("/sync")
    public ResponseEntity<String> receiveNflData(
            @RequestHeader(value = "X-WEBHOOK-SECRET", required = false) String secret,
            @RequestBody NflTeamMetricsDTO[] payload) {

        if (!"super_secret_pi_key_2026".equals(secret)) {
            return ResponseEntity.status(401).body("Unauthorized: Invalid Secret Key");
        }

        // Index incoming stats by full team name (case-insensitive)
        Map<String, NflTeamMetricsDTO> statsByTeam = new HashMap<>();
        for (NflTeamMetricsDTO teamStats : payload) {
            if (teamStats.teamName() != null) {
                statsByTeam.put(teamStats.teamName().toLowerCase(), teamStats);
            }
        }

        List<Game> allGames = gameRepo.findAll();
        int updatedCount = 0;

        for (Game game : allGames) {
            if (Boolean.TRUE.equals(game.getCompleted())) {
                continue;
            }
            if (game.getAwayTeam() == null || game.getHomeTeam() == null) {
                continue;
            }

            NflTeamMetricsDTO awayStats = statsByTeam.get(game.getAwayTeam().toLowerCase());
            NflTeamMetricsDTO homeStats = statsByTeam.get(game.getHomeTeam().toLowerCase());

            if (awayStats != null && homeStats != null) {
                analyticsService.updateGameMetrics(
                        game.getId(),
                        awayStats.epa(),
                        homeStats.epa(),
                        awayStats.successRate(),
                        homeStats.successRate(),
                        awayStats.havoc(),
                        homeStats.havoc()
                );
                updatedCount++;
            }
        }

        String summary = "Synced NFL analytics for " + updatedCount + " active games.";
        System.out.println("✅ " + summary);
        return ResponseEntity.ok(summary);
    }
}