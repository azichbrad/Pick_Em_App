package com.pickem.app.controller;

import com.pickem.app.service.api.CfbdSyncService; // Adjust if your package structure differs
import com.pickem.app.service.game.ScoreAndGradingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    private final ScoreAndGradingService gradingService;
    private final CfbdSyncService cfbdSyncService;

    public TestController(ScoreAndGradingService gradingService, CfbdSyncService cfbdSyncService) {
        this.gradingService = gradingService;
        this.cfbdSyncService = cfbdSyncService;
    }

    @GetMapping("/api/test-espn")
    public String testEspn() {
        gradingService.hourlyActiveSync();
        return "ESPN fetch fired. Check console!";
    }

    @GetMapping("/api/debug-teams")
    public String debugTeams() {
        gradingService.printTeamNameComparison();
        return "Debug printed to console! Check your IntelliJ run logs.";
    }

    @GetMapping("/api/test-cfbd")
    public String testCfbdSync(@RequestParam(defaultValue = "2026") int year) {
        System.out.println("Manual test triggered for CFBD Sync - Year: " + year);

        try {
            cfbdSyncService.syncSeasonAdvancedStats(year);
            return "Successfully triggered CFBD sync for " + year + ". Check your terminal and Supabase database!";
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to sync: " + e.getMessage();
        }
    }
}