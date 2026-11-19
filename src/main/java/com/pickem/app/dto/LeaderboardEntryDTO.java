package com.pickem.app.dto;

public record LeaderboardEntryDTO(
        Long playerId,
        String playerName,
        int wins,
        int losses,
        int pushes,
        double winPercentage,
        double hypotheticalProfit, // How much they won/lost
        double roi,                // Return on Investment percentage
        String aiStatusTag         // The fun AI text (e.g., "🔥 Sharp Bettor")
) {}