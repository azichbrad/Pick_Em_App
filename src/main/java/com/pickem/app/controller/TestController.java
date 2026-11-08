package com.pickem.app.controller;

import com.pickem.app.service.ScoreAndGradingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    private final ScoreAndGradingService gradingService;
    public TestController(ScoreAndGradingService gradingService) {
        this.gradingService = gradingService;
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
}