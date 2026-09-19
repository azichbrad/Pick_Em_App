package com.pickem.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pickem.app.dto.GameOddsDTO;
import com.pickem.app.dto.ScoreDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OddsService {

    @Value("${SHARP_API_KEY}")
    private String sharpApiKey;

    @Value("${odds.api.key}")
    private String oldApiKey;

    @Value("${odds.api.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Cacheable("ncaafOdds")
    public List<GameOddsDTO> getCollegeFootballOdds() {
        String baseUrl = "https://api.sharpapi.io/api/v1/odds?league=NCAAF&market=spread,total";
        return parseSharpApiResponse(fetchAllSharpApiOdds(baseUrl));
    }

    @Cacheable("nflOdds")
    public List<GameOddsDTO> getNflOdds() {
        String baseUrl = "https://api.sharpapi.io/api/v1/odds?league=NFL&market=spread,total";
        return parseSharpApiResponse(fetchAllSharpApiOdds(baseUrl));
    }

    // --- NEW: Automated Pagination Loop ---
    private String fetchAllSharpApiOdds(String apiUrl) {
        ArrayNode allData = objectMapper.createArrayNode();
        int offset = 0;
        boolean hasMore = true;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(sharpApiKey);
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

                // Check SharpAPI's pagination block to see if we need to fetch another page
                JsonNode pagination = root.path("pagination");
                if (pagination.has("has_more") && pagination.get("has_more").asBoolean()) {
                    offset = pagination.get("next_offset").asInt();

                    // THROTTLE: Pause for 2 seconds before requesting the next page to protect the 12/min limit
                    try {
                        Thread.sleep(2000);
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

    public List<ScoreDTO> getCompletedScores(String sportKey) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/sports/" + sportKey + "/scores/")
                .queryParam("apiKey", oldApiKey)
                .queryParam("daysFrom", 3)
                .toUriString();

        try {
            ResponseEntity<List<ScoreDTO>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<List<ScoreDTO>>() {}
            );
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<GameOddsDTO> getOddsForSportAndWeek(String sport, int weekNumber) {
        List<GameOddsDTO> allGames = getOddsForSport(sport);
        if (allGames == null || allGames.isEmpty()) {
            return List.of();
        }

        ZonedDateTime week1Start = "NFL".equalsIgnoreCase(sport)
                ? ZonedDateTime.of(2026, 9, 8, 0, 0, 0, 0, ZoneId.of("America/Los_Angeles"))
                : ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneId.of("America/Los_Angeles"));

        Instant windowStart = week1Start.plusDays((weekNumber - 1) * 7L).toInstant();
        Instant windowEnd = week1Start.plusDays(weekNumber * 7L).toInstant();

        return allGames.stream()
                .filter(game -> game.commenceTime() != null &&
                        !game.commenceTime().isBefore(windowStart) &&
                        game.commenceTime().isBefore(windowEnd))
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
        List<GameOddsDTO> formattedGames = new ArrayList<>();
        if (jsonBody == null || jsonBody.isBlank()) return formattedGames;

        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode dataArray = root.path("data");
            Map<String, GameDataBuilder> gamesMap = new HashMap<>();

            if (dataArray.isArray()) {
                for (JsonNode node : dataArray) {
                    if (node.has("is_main_line") && !node.get("is_main_line").asBoolean()) continue;

                    String marketType = node.path("market_type").asText();
                    if (marketType.contains("quarter") || marketType.contains("half")) continue;

                    String eventId = node.path("event_id").asText();
                    GameDataBuilder gb = gamesMap.computeIfAbsent(eventId, id -> {
                        GameDataBuilder newGb = new GameDataBuilder();
                        newGb.id = id;
                        newGb.homeTeam = node.path("home_team").asText();
                        newGb.awayTeam = node.path("away_team").asText();
                        newGb.commenceTime = ZonedDateTime.parse(node.path("event_start_time").asText()).toInstant();
                        return newGb;
                    });

                    double line = node.path("line").asDouble();
                    ObjectNode outcomeNode = objectMapper.createObjectNode();
                    outcomeNode.put("point", line);

                    if (marketType.contains("spread")) {
                        // FIX: Use team_side ("home" or "away") instead of the raw selection string!
                        String teamSide = node.path("team_side").asText();
                        if ("away".equalsIgnoreCase(teamSide)) {
                            outcomeNode.put("name", gb.awayTeam);
                        } else {
                            outcomeNode.put("name", gb.homeTeam);
                        }
                        gb.spreadOutcomes.add(outcomeNode);
                    } else if (marketType.contains("total")) {
                        // FIX: Use selection_type ("over" or "under") instead of the raw selection string!
                        String selType = node.path("selection_type").asText();
                        String capitalized = selType.substring(0, 1).toUpperCase() + selType.substring(1).toLowerCase();
                        outcomeNode.put("name", capitalized);
                        gb.totalOutcomes.add(outcomeNode);
                    }
                }
            }

            for (GameDataBuilder gb : gamesMap.values()) {
                ArrayNode marketsArray = objectMapper.createArrayNode();

                if (!gb.spreadOutcomes.isEmpty()) {
                    ObjectNode spreadMarket = objectMapper.createObjectNode();
                    spreadMarket.put("key", "spread");
                    ArrayNode outArray = objectMapper.createArrayNode();
                    gb.spreadOutcomes.forEach(outArray::add);
                    spreadMarket.set("outcomes", outArray);
                    marketsArray.add(spreadMarket);
                }

                if (!gb.totalOutcomes.isEmpty()) {
                    ObjectNode totalMarket = objectMapper.createObjectNode();
                    totalMarket.put("key", "total");
                    ArrayNode outArray = objectMapper.createArrayNode();
                    gb.totalOutcomes.forEach(outArray::add);
                    totalMarket.set("outcomes", outArray);
                    marketsArray.add(totalMarket);
                }

                List<GameOddsDTO.BookmakerDTO> bookmakers = new ArrayList<>();
                if (!marketsArray.isEmpty()) {
                    ObjectNode bookmakerNode = objectMapper.createObjectNode();
                    bookmakerNode.put("key", "sharpapi");
                    bookmakerNode.put("title", "SharpAPI");
                    bookmakerNode.set("markets", marketsArray);

                    GameOddsDTO.BookmakerDTO bmDTO = objectMapper.convertValue(
                            bookmakerNode,
                            new TypeReference<GameOddsDTO.BookmakerDTO>() {}
                    );
                    bookmakers.add(bmDTO);
                }

                formattedGames.add(new GameOddsDTO(gb.id, gb.homeTeam, gb.awayTeam, gb.commenceTime, bookmakers));
            }

        } catch (Exception e) {
            System.err.println("Error parsing SharpAPI response: " + e.getMessage());
        }

        return formattedGames;
    }

    private static class GameDataBuilder {
        String id;
        String homeTeam;
        String awayTeam;
        Instant commenceTime;
        List<JsonNode> spreadOutcomes = new ArrayList<>();
        List<JsonNode> totalOutcomes = new ArrayList<>();
    }
}