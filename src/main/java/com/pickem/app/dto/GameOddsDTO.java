package com.pickem.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.OffsetDateTime;

public record GameOddsDTO(
        // Map back to the correct keys for the /odds endpoint
        @JsonProperty("event_id") String eventId,
        @JsonProperty("home_team") String homeTeam,
        @JsonProperty("away_team") String awayTeam,

        @JsonProperty("event_start_time") String rawEventStartTime,

        @JsonProperty("market_type") String marketType,
        @JsonProperty("selection") String selection,
        @JsonProperty("line") Double line,
        @JsonProperty("odds_american") Integer oddsAmerican
) {
    public Instant eventStartTime() {
        if (rawEventStartTime == null || rawEventStartTime.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(rawEventStartTime).toInstant();
    }
}