package com.pickem.app.controller;

import com.pickem.app.dto.LeaderboardEntryDTO;
import com.pickem.app.service.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leagues")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * Fetches the dynamic leaderboard for a league.
     * Example usage: /api/leagues/1/leaderboard?sport=NFL&unitStake=50
     */
    @GetMapping("/{leagueId}/leaderboard")
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(
            @PathVariable Long leagueId,
            @RequestParam(defaultValue = "ALL") String sport,
            @RequestParam(defaultValue = "100.0") double unitStake
    ) {
        List<LeaderboardEntryDTO> leaderboard = leaderboardService.generateLeaderboard(leagueId, sport, unitStake);
        return ResponseEntity.ok(leaderboard);
    }
}