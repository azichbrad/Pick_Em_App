package com.pickem.app.dto;

public record TeamAtsSplitDTO(
        String teamName,
        String situation,
        int wins,
        int losses,
        int pushes,
        double coverPct,
        double atsMargin
) {}