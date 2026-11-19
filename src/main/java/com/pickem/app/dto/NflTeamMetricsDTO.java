package com.pickem.app.dto;

public record NflTeamMetricsDTO(
        String teamName,
        Double epa,
        Double successRate,
        Double havoc
) {}