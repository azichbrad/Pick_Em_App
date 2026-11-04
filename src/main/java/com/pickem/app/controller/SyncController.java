package com.pickem.app.controller;

import com.pickem.app.service.ScoreAndGradingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final ScoreAndGradingService scoreService;

    public SyncController(ScoreAndGradingService scoreService) {
        this.scoreService = scoreService;
    }

    @GetMapping("/scores")
    public String forceScoreSync() {
        scoreService.hourlyActiveSync();
        return "Hourly score sync triggered manually!";
    }

    @GetMapping("/scores-deep")
    public String forceDeepScoreSync() {
        scoreService.nightlyDeepSync();
        return "Deep 10-day score sync triggered manually!";
    }
}