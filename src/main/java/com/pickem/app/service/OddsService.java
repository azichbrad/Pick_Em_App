package com.pickem.app.service;

import com.pickem.app.dto.GameOddsDTO;
import com.pickem.app.dto.ScoreDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Service
public class OddsService {

    // Pulls your API key from application.properties / .env
    @Value("${odds.api.key}")
    private String apiKey;

    @Value("${odds.api.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // The @Cacheable annotation saves the result in memory so you don't drain your API quota
    @Cacheable("ncaafOdds")
    public List<GameOddsDTO> getCollegeFootballOdds() {
        String url = baseUrl + "/americanfootball_ncaaf/odds/?apiKey=" + apiKey + "&regions=us&markets=spreads,totals";

        ResponseEntity<List<GameOddsDTO>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<GameOddsDTO>>() {}
        );

        return response.getBody();
    }

    @Cacheable("nflOdds")
    public List<GameOddsDTO> getNflOdds() {
        String url = baseUrl + "/americanfootball_nfl/odds/?apiKey=" + apiKey + "&regions=us&markets=spreads,totals";

        ResponseEntity<List<GameOddsDTO>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<GameOddsDTO>>() {}
        );

        return response.getBody();
    }

    public List<ScoreDTO> getCompletedScores(String sportKey) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/sports/" + sportKey + "/scores/")
                .queryParam("apiKey", apiKey)
                .queryParam("daysFrom", 3) // Looks back at the last 3 days of finished games
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

        int gamesPerWeek = "NFL".equalsIgnoreCase(sport) ? 16 : 60;
        int startIndex = (Math.max(1, weekNumber) - 1) * gamesPerWeek;
        int endIndex = Math.min(startIndex + gamesPerWeek, allGames.size());

        if (startIndex >= allGames.size()) {
            return List.of(); // Week is out of range
        }

        return allGames.subList(startIndex, endIndex);
    }

    // Add this method to OddsService.java:
    public List<GameOddsDTO> getOddsForSport(String sport) {
        if ("NFL".equalsIgnoreCase(sport)) {
            return getNflOdds();
        } else {
            return getCollegeFootballOdds();
        }
    }
}