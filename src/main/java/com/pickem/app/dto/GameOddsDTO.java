package com.pickem.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.OffsetDateTime;

public record GameOddsDTO(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("home_team") String homeTeam,
        @JsonProperty("away_team") String awayTeam,

        // 1. Tell Jackson to safely grab the timestamp as a raw string so it doesn't crash
        @JsonProperty("event_start_time") String rawEventStartTime,

        @JsonProperty("market_type") String marketType,
        @JsonProperty("selection") String selection,
        @JsonProperty("line") Double line,
        @JsonProperty("odds_american") Integer oddsAmerican
) {
    // 2. Provide a helper method that outputs an Instant for the rest of your app.
    // OffsetDateTime effortlessly parses timestamps both with AND without the ":00" seconds!
    public Instant eventStartTime() {
        if (rawEventStartTime == null || rawEventStartTime.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(rawEventStartTime).toInstant();
    }
}