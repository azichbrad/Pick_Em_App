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
import java.util.List;

@Service
public class OddsService {

    @Value("${SHARP_API_KEY}")
    private String sharpApiKey;

    @Value("${odds.api.key}")
    private String oldApiKey;

    @Value("${odds.api.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // FIXED: Actually register the JavaTimeModule to prevent the Jackson crash
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Cacheable("ncaafOdds")
    public List<GameOddsDTO> getCollegeFootballOdds() {
        // FIXED: Using exact market_type names to filter out 1st-half/quarter noise
        String baseUrl = "https://api.sharpapi.io/api/v1/odds?league=NCAAF&market_type=point_spread,total_points&limit=200";
        return parseSharpApiResponse(fetchAllSharpApiOdds(baseUrl));
    }

    @Cacheable("nflOdds")
    public List<GameOddsDTO> getNflOdds() {
        // FIXED: Using exact market_type names to filter out 1st-half/quarter noise
        String baseUrl = "https://api.sharpapi.io/api/v1/odds?league=NFL&market_type=point_spread,total_points&limit=200";
        return parseSharpApiResponse(fetchAllSharpApiOdds(baseUrl));
    }

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

        // FIXED: NCAAF shifted to August 30 (Sunday) to capture Thursday-Saturday games
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
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(jsonBody);

            com.fasterxml.jackson.databind.JsonNode dataNode = root.has("data") ? root.get("data") : root;

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