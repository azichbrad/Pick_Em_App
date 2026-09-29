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
        return getCompletedScores(sport, 3);
    }

    // Unified Highlightly 2-Step Score Fetcher for both NFL and NCAAF
    public List<ScoreDTO> getCompletedScores(String sport, int daysBack) {
        List<ScoreDTO> finalScores = new ArrayList<>();
        if (highlightlyApiKey == null || highlightlyApiKey.isEmpty()) {
            return finalScores;
        }

        LocalDate today = LocalDate.now(ZoneId.of("America/Los_Angeles"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-rapidapi-key", highlightlyApiKey);
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Step 1: Iterate through the date window and get the match list
        for (int i = 0; i <= daysBack; i++) {
            String dateStr = today.minusDays(i).format(formatter);
            String matchesUrl = "https://sports.highlightly.net/american-football/matches?date=" + dateStr;

            try {
                ResponseEntity<JsonNode> response = restTemplate.exchange(matchesUrl, HttpMethod.GET, entity, JsonNode.class);
                JsonNode root = response.getBody();
                JsonNode matches = root != null && root.has("data") ? root.path("data") : root;

                if (matches != null && matches.isArray()) {
                    for (JsonNode match : matches) {
                        boolean completed = match.path("completed").asBoolean(false);
                        if (!completed) continue;

                        // Filter correctly by sport/league type
                        String league = match.path("league").path("name").asText(match.path("league").asText()).toLowerCase();
                        boolean isNcaaf = sport.toLowerCase().contains("ncaaf");

                        if (isNcaaf && !league.contains("ncaa") && !league.contains("fbs") && !league.contains("college")) {
                            continue;
                        }
                        if (!isNcaaf && !league.contains("nfl")) {
                            continue;
                        }

                        String matchId = match.path("id").asText();

                        // Step 2: Use the match ID to fetch the detailed box score
                        ScoreDTO detailedScore = fetchHighlightlyBoxScore(matchId, sport, headers);
                        if (detailedScore != null) {
                            boolean alreadyAdded = finalScores.stream().anyMatch(s -> s.id().equals(matchId));
                            if (!alreadyAdded) {
                                finalScores.add(detailedScore);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to fetch Highlightly matches for " + dateStr + ": " + e.getMessage());
            }
        }

        return finalScores;
    }

    private ScoreDTO fetchHighlightlyBoxScore(String matchId, String sport, HttpHeaders headers) {
        String boxScoreUrl = "https://sports.highlightly.net/american-football/matches/" + matchId + "/boxscore";

        try {
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(boxScoreUrl, HttpMethod.GET, entity, JsonNode.class);
            JsonNode boxRoot = response.getBody();

            if (boxRoot != null) {
                String homeTeam = boxRoot.path("homeTeam").path("name").asText(boxRoot.path("home_team").asText());
                String awayTeam = boxRoot.path("awayTeam").path("name").asText(boxRoot.path("away_team").asText());
                String homeScore = boxRoot.path("homeScore").asText(boxRoot.path("home_points").asText());
                String awayScore = boxRoot.path("awayScore").asText(boxRoot.path("away_points").asText());

                List<ScoreDTO.TeamScoreDTO> teamScores = List.of(
                        new ScoreDTO.TeamScoreDTO(homeTeam, homeScore),
                        new ScoreDTO.TeamScoreDTO(awayTeam, awayScore)
                );

                return new ScoreDTO(matchId, sport, true, homeTeam, awayTeam, teamScores);
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to fetch box score for match ID " + matchId + ": " + e.getMessage());
        }
        return null;
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