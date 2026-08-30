package com.pickem.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GameOddsDTO(
        String id,
        @JsonProperty("home_team") String homeTeam,
        @JsonProperty("away_team") String awayTeam,
        @JsonProperty("commence_time") Instant commenceTime,
        List<BookmakerDTO> bookmakers
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookmakerDTO(
            String title,
            List<MarketDTO> markets
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketDTO(
            String key,
            List<OutcomeDTO> outcomes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutcomeDTO(
            String name,
            Double price,
            Double point
    ) {}
}