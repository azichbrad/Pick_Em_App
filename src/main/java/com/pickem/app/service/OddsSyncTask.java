package com.pickem.app.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OddsSyncTask {

    private final GameSyncService gameSyncService;

    public OddsSyncTask(GameSyncService gameSyncService) {
        this.gameSyncService = gameSyncService;
    }

    // Weekend slate: runs every 15 minutes on Saturday and Sunday
    @Scheduled(cron = "0 0/15 * * * SAT,SUN", zone = "America/Los_Angeles")
    public void weekendOddsSync() {
        System.out.println("🏈 Running Weekend Odds Sync...");
        gameSyncService.syncAllOdds();
    }

    // Weekdays: runs once every 2 hours to conserve SharpAPI quotas
    @Scheduled(cron = "0 0 0/2 * * MON-FRI", zone = "America/Los_Angeles")
    public void weekdayOddsSync() {
        System.out.println("📅 Running Weekday Odds Sync...");
        gameSyncService.syncAllOdds();
    }
}