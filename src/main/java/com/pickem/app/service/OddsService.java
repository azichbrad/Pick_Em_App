package com.pickem.app.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pickem.app.dto.GameOddsDTO;
import com.pickem.app.dto.ScoreDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class OddsService {

    @Value("${SHARP_API_KEY}")
    private String sharpApiKey;

    @Value("${cfbd.api.key:}")
    private String cfbdApiKey;

    @Value("${HIGHLIGHTLY_API_KEY:}")
    private String highlightlyApiKey;

    @Value("${odds.api.key:}")
    private String oldApiKey;

    @Value("${odds.api.base-url:}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Cacheable("ncaafOdds")
    public List<GameOddsDTO> getCollegeFootballOdds() {
        String url = "https://api.sharpapi.io/api/v1/events?sport=football&league=NCAAF&sportsbook=fanduel&limit=200";
        return parseSharpApiResponse(fetchAllSharpApiOdds(url));
    }

    @Cacheable("nflOdds")
    public List<GameOddsDTO> getNflOdds() {
        String url = "https://api.sharpapi.io/api/v1/events?sport=football&league=NFL&sportsbook=fanduel&limit=200";
        return parseSharpApiResponse(fetchAllSharpApiOdds(url));
    }

    private String fetchAllSharpApiOdds(String apiUrl) {
        ArrayNode allData = objectMapper.createArrayNode();
        int offset = 0;
        boolean hasMore = true;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(sharpApiKey);
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        while (hasMore) {
            String pagedUrl = apiUrl + "&offset=" + offset;
            try {
                ResponseEntity<String> response = restTemplate.exchange(pagedUrl, HttpMethod.GET, entity, String.class);
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode dataArray = root.path("data");

                if (dataArray.isArray()) {
                    for (JsonNode node : dataArray) {
                        allData.add(node);
                    }
                }

                JsonNode pagination = root.path("pagination");
                if (pagination.has("has_more") && pagination.get("has_more").asBoolean()) {
                    offset = pagination.get("next_offset").asInt();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    hasMore = false;
                }
            } catch (Exception e) {
                System.err.println("API Error at offset " + offset + ": " + e.getMessage());
                hasMore = false;
            }
        }

        ObjectNode combinedRoot = objectMapper.createObjectNode();
        combinedRoot.set("data", allData);
        return combinedRoot.toString();
    }

    public List<ScoreDTO> getCompletedScores(String sport) {
        return getCompletedScores(sport, 3);
    }

    public List<ScoreDTO> getCompletedScores(String sport, int daysBack) {
        if (sport.toLowerCase().contains("ncaaf") && cfbdApiKey != null && !cfbdApiKey.isEmpty()) {
            return getCfbdScores(sport, daysBack);
        }

        if (sport.toLowerCase().contains("nfl") && highlightlyApiKey != null && !highlightlyApiKey.isEmpty()) {
            return getHighlightlyNflScores(sport, daysBack);
        }

        return new ArrayList<>();
    }

    private List<ScoreDTO> getHighlightlyNflScores(String sport, int daysBack) {
        List<ScoreDTO> finalScores = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.of("America/Los_Angeles"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 0; i <= daysBack; i++) {
            String dateStr = today.minusDays(i).format(formatter);
            String url = "https://sports.highlightly.net/american-football/matches?date=" + dateStr;

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("x-rapidapi-key", highlightlyApiKey);
                headers.set("Accept", "application/json");
                HttpEntity<String> entity = new HttpEntity<>(headers);

                ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
                JsonNode root = response.getBody();

                System.out.println("🔍 Highlightly Response for " + dateStr + ": " + (root != null ? root.toString().substring(0, Math.min(root.toString().length(), 200)) : "null"));

                JsonNode matches = root != null && root.has("data") ? root.path("data") : root;

                if (matches != null && matches.isArray()) {
                    for (JsonNode match : matches) {
                        boolean completed = match.path("completed").asBoolean(false);
                        if (!completed) continue;

                        String gameId = match.path("id").asText();
                        String homeTeam = match.path("homeTeam").path("name").asText(match.path("home_team").asText());
                        String awayTeam = match.path("awayTeam").path("name").asText(match.path("away_team").asText());
                        String homeScore = match.path("homeScore").asText(match.path("home_points").asText());
                        String awayScore = match.path("awayScore").asText(match.path("away_points").asText());

                        List<ScoreDTO.TeamScoreDTO> teamScores = List.of(
                                new ScoreDTO.TeamScoreDTO(homeTeam, homeScore),
                                new ScoreDTO.TeamScoreDTO(awayTeam, awayScore)
                        );

                        boolean alreadyAdded = finalScores.stream().anyMatch(s -> s.id().equals(gameId));
                        if (!alreadyAdded) {
                            finalScores.add(new ScoreDTO(gameId, sport, true, homeTeam, awayTeam, teamScores));
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Highlightly error for " + dateStr + ": " + e.getMessage());
            }
        }

        return finalScores;
    }

    private List<ScoreDTO> getCfbdScores(String sport, int daysBack) {
        List<ScoreDTO> finalScores = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.of("America/Los_Angeles"));
        int currentYear = today.getYear();

        // Query by specific current week / regular season to get clean, immediate results
        String url = "https://api.collegefootballdata.com/games?year=" + currentYear + "&seasonType=regular";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(cfbdApiKey);
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode games = response.getBody();

            if (games != null && games.isArray()) {
                for (JsonNode game : games) {
                    boolean completed = game.path("completed").asBoolean(false);
                    if (!completed) continue;

                    String startDateStr = game.path("start_date").asText();
                    if (startDateStr != null && !startDateStr.isEmpty()) {
                        Instant start = Instant.parse(startDateStr);
                        LocalDate gameDate = start.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate();

                        // Check if the game occurred within our lookback window (e.g., last 7 days)
                        if (gameDate.isBefore(today.minusDays(daysBack)) || gameDate.isAfter(today)) {
                            continue;
                        }
                    }

                    String gameId = game.path("id").asText();
                    String homeTeam = game.path("home_team").asText();
                    String awayTeam = game.path("away_team").asText();
                    String homeScore = game.path("home_points").asText();
                    String awayScore = game.path("away_points").asText();

                    List<ScoreDTO.TeamScoreDTO> teamScores = List.of(
                            new ScoreDTO.TeamScoreDTO(homeTeam, homeScore),
                            new ScoreDTO.TeamScoreDTO(awayTeam, awayScore)
                    );

                    finalScores.add(new ScoreDTO(gameId, sport, true, homeTeam, awayTeam, teamScores));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to fetch CFBD NCAAF scores: " + e.getMessage());
        }

        return finalScores;
    }

    public List<GameOddsDTO> getOddsForSportAndWeek(String sport, int weekNumber) {
        List<GameOddsDTO> allGames = getOddsForSport(sport);
        if (allGames == null || allGames.isEmpty()) {
            return List.of();
        }

        ZonedDateTime week1Start = "NFL".equalsIgnoreCase(sport)
                ? ZonedDateTime.of(2026, 9, 8, 0, 0, 0, 0, ZoneId.of("America/Los_Angeles"))
                : ZonedDateTime.of(2026, 8, 30, 0, 0, 0, 0, ZoneId.of("America/Los_Angeles"));

        Instant windowStart = week1Start.plusDays((weekNumber - 1) * 7L).toInstant();
        Instant windowEnd = week1Start.plusDays(weekNumber * 7L).toInstant();

        return allGames.stream()
                .filter(game -> game.eventStartTime() != null &&
                        !game.eventStartTime().isBefore(windowStart) &&
                        game.eventStartTime().isBefore(windowEnd))
                .toList();
    }

    public List<GameOddsDTO> getOddsForSport(String sport) {
        if ("NFL".equalsIgnoreCase(sport)) {
            return getNflOdds();
        } else {
            return getCollegeFootballOdds();
        }
    }

    private List<GameOddsDTO> parseSharpApiResponse(String jsonBody) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode dataNode = root.has("data") ? root.get("data") : root;

            return objectMapper.convertValue(
                    dataNode,
                    new com.fasterxml.jackson.core.type.TypeReference<List<GameOddsDTO>>() {}
            );
        } catch (Exception e) {
            System.err.println("Failed to parse SharpAPI response: " + e.getMessage());
            return List.of();
        }
    }
}