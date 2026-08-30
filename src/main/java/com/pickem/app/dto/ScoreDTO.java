package com.pickem.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreDTO(
        String id,
        String sport_key,
        Boolean completed,
        @JsonProperty("home_team") String homeTeam,
        @JsonProperty("away_team") String awayTeam,
        List<TeamScoreDTO> scores
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamScoreDTO(
            String name,
            String score
    ) {}
}