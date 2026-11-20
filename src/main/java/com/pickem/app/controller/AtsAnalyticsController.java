package com.pickem.app.controller;

import com.pickem.app.dto.TeamAtsSplitDTO;
import com.pickem.app.service.api.CfbdSyncService;
import com.pickem.app.service.game.TeamAtsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics/ats")
public class AtsAnalyticsController {

    private final TeamAtsService teamAtsService;
    private final CfbdSyncService cfbdSyncService;

    @Value("${NFL_WEBHOOK_SECRET}")
    private String webhookSecret;

    public AtsAnalyticsController(TeamAtsService teamAtsService, CfbdSyncService cfbdSyncService) {
        this.teamAtsService = teamAtsService;
        this.cfbdSyncService = cfbdSyncService;
    }

    public record AtsSyncRequest(String sport, int season, List<TeamAtsSplitDTO> splits) {}

    @PostMapping("/sync")
    public ResponseEntity<String> syncAtsSplits(
            @RequestHeader(value = "X-WEBHOOK-SECRET", required = false) String providedSecret,
            @RequestBody AtsSyncRequest request) {

        if (providedSecret == null || !providedSecret.equals(webhookSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook secret");
        }

        if (request == null || request.sport() == null || request.splits() == null) {
            return ResponseEntity.badRequest().body("Missing sport or splits payload");
        }

        int count = teamAtsService.upsertSplits(request.sport(), request.season(), request.splits());
        return ResponseEntity.ok("Synced " + count + " ATS split rows for " + request.sport() + " " + request.season());
    }

    @PostMapping("/sync/cfb")
    public ResponseEntity<String> syncCfbAts(
            @RequestHeader(value = "X-WEBHOOK-SECRET", required = false) String providedSecret,
            @RequestParam(defaultValue = "2026") int season) {

        if (providedSecret == null || !providedSecret.equals(webhookSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook secret");
        }

        int count = cfbdSyncService.syncSeasonAtsSplits(season);
        return ResponseEntity.ok("Synced " + count + " NCAAF ATS split rows for " + season);
    }
}