package com.pickem.app.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OddsSyncTask {

    private final GameSyncService gameSyncService;

    public OddsSyncTask(GameSyncService gameSyncService) {
        this.gameSyncService = gameSyncService;
    }

    // FIXED: Only one thread executes. Waits 5 seconds after boot, then every 2 mins.
    @Scheduled(initialDelay = 5000, fixedDelay = 120000)
    public void syncOddsToDatabase() {
        System.out.println("Starting odds sync trigger...");

        // Let the perfected logic in GameSyncService handle the heavy lifting!
        gameSyncService.syncAllOdds();

        System.out.println("Odds sync trigger completed.");
    }
}