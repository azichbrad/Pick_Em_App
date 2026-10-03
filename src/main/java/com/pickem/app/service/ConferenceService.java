package com.pickem.app.service;

import com.pickem.app.dto.TeamDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
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
public class ConferenceService {

    @Value("${cfbd.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<Integer, Map<String, Integer>> weeklyTop25Cache = new HashMap<>();

    // Now maps the School Name to the entire TeamDTO object
    @Cacheable("teams")
    public Map<String, TeamDTO> getTeamDataMap() {
        String url = "https://api.collegefootballdata.com/teams/fbs";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<List<TeamDTO>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<TeamDTO>>() {}
        );

        Map<String, TeamDTO> teamMap = new HashMap<>();
        if (response.getBody() != null) {
            for (TeamDTO team : response.getBody()) {
                teamMap.put(team.school(), team);
            }
        }
        return teamMap;
    }

    public Map<String, Integer> getApTop25(int week) {
        // Return from cache if we already have non-empty rankings for this week
        if (weeklyTop25Cache.containsKey(week) && !weeklyTop25Cache.get(week).isEmpty()) {
            return weeklyTop25Cache.get(week);
        }

        Map<String, Integer> top25 = fetchRankingsFromCfbd(week);

        // If next week's poll hasn't dropped yet (empty), fall back to the previous week's rankings!
        if (top25.isEmpty() && week > 0) {
            top25 = fetchRankingsFromCfbd(week - 1);
        } else if (!top25.isEmpty()) {
            // Only cache if we actually got rankings (so it re-checks once Sunday's poll drops)
            weeklyTop25Cache.put(week, top25);
        }

        return top25;
    }

    private Map<String, Integer> fetchRankingsFromCfbd(int week) {
        Map<String, Integer> top25 = new HashMap<>();
        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + this.apiKey);
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            String url = "https://api.collegefootballdata.com/rankings?year=2026&week=" + week + "&seasonType=regular";
            org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode[]> response =
                    restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, com.fasterxml.jackson.databind.JsonNode[].class);

            if (response.getBody() != null && response.getBody().length > 0) {
                com.fasterxml.jackson.databind.JsonNode polls = response.getBody()[0].get("polls");
                if (polls != null) {
                    for (com.fasterxml.jackson.databind.JsonNode poll : polls) {
                        if ("AP Top 25".equals(poll.get("poll").asText())) {
                            for (com.fasterxml.jackson.databind.JsonNode rankNode : poll.get("ranks")) {
                                top25.put(rankNode.get("school").asText(), rankNode.get("rank").asInt());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch AP Top 25 for week " + week + ": " + e.getMessage());
        }
        return top25;
    }
}