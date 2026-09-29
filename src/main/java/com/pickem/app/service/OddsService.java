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
        String cursor = null;
        int offset = 0;
        boolean hasMore = true;
        int pageCount = 0;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(sharpApiKey);
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        while (hasMore && pageCount < 20) { // Safety break limit
            String pagedUrl = apiUrl;
            if (cursor != null && !cursor.isEmpty()) {
                pagedUrl += (apiUrl.contains("?") ? "&" : "?") + "cursor=" + cursor;
            } else if (offset > 0) {
                pagedUrl += (apiUrl.contains("?") ? "&" : "?") + "offset=" + offset;
            }

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
                    // Check for cursor pagination first, fallback to offset if needed
                    if (pagination.has("next_cursor") && !pagination.get("next_cursor").isNull()) {
                        cursor = pagination.get("next_cursor").asText();
                    } else if (pagination.has("next_offset")) {
                        offset = pagination.get("next_offset").asInt();
                    } else {
                        hasMore = false;
                    }

                    pageCount++;
                    try {
                        Thread.sleep(5000); // 5-second throttle to respect rate limits
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    hasMore = false;
                }
            } catch (Exception e) {
                System.err.println("API Pagination Error: " + e.getMessage());
                hasMore = false;
            }
        }

        ObjectNode combinedRoot = objectMapper.createObjectNode();
        combinedRoot.set("data", allData);
        return combinedRoot.toString();
    }

    public List<ScoreDTO> getCompletedScores(String sport) {
        return getCompletedScores(sport, 10); // 10-day lookback to catch last week's games
    }

    public List<ScoreDTO> getCompletedScores(String sport, int daysBack) {
        List<ScoreDTO> finalScores = new ArrayList<>();
        if (highlightlyApiKey == null || highlightlyApiKey.isEmpty()) {
            System.err.println("❌ Highlightly API Key is missing!");
            return finalScores;
        }

        LocalDate today = LocalDate.now(ZoneId.of("America/Los_Angeles"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-rapidapi-key", highlightlyApiKey);
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String leagueParam = sport.toLowerCase().contains("ncaaf") ? "NCAA" : "NFL";

        for (int i = 0; i <= daysBack; i++) {
            String dateStr = today.minusDays(i).format(formatter);
            int offset = 0;
            boolean hasMore = true;

            while (hasMore) {
                // Pass the offset parameter to page through high-volume days
                String url = "https://american-football.highlightly.net/matches?league=" + leagueParam + "&date=" + dateStr + "&offset=" + offset;

                try {
                    System.out.println("🔍 Fetching Highlightly matches for " + leagueParam + " on " + dateStr + " (offset: " + offset + ")");
                    ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
                    JsonNode root = response.getBody();

                    JsonNode matches = root != null && root.has("data") ? root.path("data") : root;

                    if (matches != null && matches.isArray()) {
                        for (JsonNode match : matches) {
                            JsonNode state = match.path("state");
                            String status = state.path("description").asText("");

                            if (!"Finished".equalsIgnoreCase(status)) {
                                continue;
                            }

                            String gameId = match.path("id").asText();
                            String homeTeam = match.path("homeTeam").path("displayName").asText(match.path("homeTeam").path("name").asText(""));
                            String awayTeam = match.path("awayTeam").path("displayName").asText(match.path("awayTeam").path("name").asText(""));

                            String currentScore = state.path("score").path("current").asText("");
                            String homeScore = "0";
                            String awayScore = "0";

                            if (currentScore.contains(" - ")) {
                                String[] parts = currentScore.split(" - ");
                                if (parts.length == 2) {
                                    homeScore = parts[0].trim();
                                    awayScore = parts[1].trim();
                                }
                            }

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

                    // Check pagination to fetch the next page if there are more than 100 matches
                    JsonNode pagination = root != null ? root.path("pagination") : null;
                    if (pagination != null && !pagination.isMissingNode()) {
                        int totalCount = pagination.path("totalCount").asInt(0);
                        int currentLimit = pagination.path("limit").asInt(100);
                        offset += currentLimit;

                        if (offset >= totalCount) {
                            hasMore = false;
                        } else {
                            Thread.sleep(1500); // Throttle before the next page request
                        }
                    } else {
                        hasMore = false; // Safety break if pagination node is missing
                    }

                } catch (Exception e) {
                    System.err.println("❌ Highlightly error for " + leagueParam + " on " + dateStr + " at offset " + offset + ": " + e.getMessage());
                    hasMore = false;
                }
            }

            // Throttle before querying the next date to prevent rate limits
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("✅ Total scores successfully parsed from Highlightly for " + sport + ": " + finalScores.size());
        return finalScores;
    }

//    private ScoreDTO fetchHighlightlyBoxScore(String matchId, String sport, HttpHeaders headers) {
//        String boxScoreUrl = "https://sports.highlightly.net/american-football/matches/" + matchId + "/boxscore";
//
//        try {
//            HttpEntity<String> entity = new HttpEntity<>(headers);
//            ResponseEntity<JsonNode> response = restTemplate.exchange(boxScoreUrl, HttpMethod.GET, entity, JsonNode.class);
//            JsonNode boxRoot = response.getBody();
//
//            if (boxRoot != null) {
//                String homeTeam = boxRoot.path("homeTeam").path("name").asText(boxRoot.path("home_team").asText());
//                String awayTeam = boxRoot.path("awayTeam").path("name").asText(boxRoot.path("away_team").asText());
//                String homeScore = boxRoot.path("homeScore").asText(boxRoot.path("home_points").asText());
//                String awayScore = boxRoot.path("awayScore").asText(boxRoot.path("away_points").asText());
//
//                List<ScoreDTO.TeamScoreDTO> teamScores = List.of(
//                        new ScoreDTO.TeamScoreDTO(homeTeam, homeScore),
//                        new ScoreDTO.TeamScoreDTO(awayTeam, awayScore)
//                );
//
//                return new ScoreDTO(matchId, sport, true, homeTeam, awayTeam, teamScores);
//            }
//        } catch (Exception e) {
//            System.err.println("❌ Failed to fetch box score for match ID " + matchId + ": " + e.getMessage());
//        }
//        return null;
//    }
//
//    public List<GameOddsDTO> getOddsForSportAndWeek(String sport, int weekNumber) {
//        List<GameOddsDTO> allGames = getOddsForSport(sport);
//        if (allGames == null || allGames.isEmpty()) {
//            return List.of();
//        }
//
//        ZonedDateTime week1Start = "NFL".equalsIgnoreCase(sport)
//                ? ZonedDateTime.of(2026, 9, 8, 0, 0, 0, 0, ZoneId.of("America/Los_Angeles"))
//                : ZonedDateTime.of(2026, 8, 30, 0, 0, 0, 0, ZoneId.of("America/Los_Angeles"));
//
//        Instant windowStart = week1Start.plusDays((weekNumber - 1) * 7L).toInstant();
//        Instant windowEnd = week1Start.plusDays(weekNumber * 7L).toInstant();
//
//        return allGames.stream()
//                .filter(game -> game.eventStartTime() != null &&
//                        !game.eventStartTime().isBefore(windowStart) &&
//                        game.eventStartTime().isBefore(windowEnd))
//                .toList();
//    }

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