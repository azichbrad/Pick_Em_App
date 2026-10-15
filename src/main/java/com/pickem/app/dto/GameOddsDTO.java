package com.pickem.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.OffsetDateTime;

public record GameOddsDTO(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("home_team") String homeTeam,
        @JsonProperty("away_team") String awayTeam,
        @JsonProperty("event_start_time") String rawEventStartTime,
        @JsonProperty("market_type") String marketType,
        @JsonProperty("selection") String selection,
        @JsonProperty("line") Double line,
        @JsonProperty("odds_american") Integer oddsAmerican,
        @JsonProperty("is_main_line") Boolean isMainLine,
        @JsonProperty("is_alternate_line") Boolean isAlternateLine
) {
    public Instant eventStartTime() {
        if (rawEventStartTime == null || rawEventStartTime.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawEventStartTime).toInstant();
        } catch (Exception e) {
            return null;
        }
    }
}