package com.pickem.app.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.pickem.app.model.Game;
import com.pickem.app.repository.GameRepository;
import com.pickem.app.service.game.GameAnalyticsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CfbdSyncService {

    private final GameAnalyticsService analyticsService;
    private final GameRepository gameRepo;
    private final RestTemplate restTemplate;

    @Value("${cfbd.api.key}")
    private String apiKey;

    public CfbdSyncService(GameAnalyticsService analyticsService, GameRepository gameRepo) {
        this.analyticsService = analyticsService;
        this.gameRepo = gameRepo;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Fetches cumulative season averages for all teams and applies them to upcoming NCAAF games.
     */
    public void syncSeasonAdvancedStats(int year) {
        System.out.println("Fetching season-long advanced stats from CFBD for year: " + year);
        String url = "https://api.collegefootballdata.com/stats/season/advanced?year=" + year;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode rootArray = response.getBody();

            if (rootArray == null || !rootArray.isArray()) return;

            // 1. Map every team's season-long stats into memory for instant lookup
            Map<String, JsonNode> teamStatsMap = new HashMap<>();
            for (JsonNode teamNode : rootArray) {
                teamStatsMap.put(teamNode.path("team").asText(), teamNode);
            }

            // 2. Fetch only unplayed NCAAF games from your database
            List<Game> upcomingNcaafGames = gameRepo.findAll().stream()
                    .filter(g -> "NCAAF".equals(g.getSport()) && !g.getCompleted())
                    .toList();

            // 3. Apply the season averages to the game analytics table
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

        } catch (Exception e) {
            System.err.println("Failed to fetch CFBD season stats: " + e.getMessage());
        }
    }
}