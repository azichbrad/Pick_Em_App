package com.pickem.app.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OddsSyncTask {

    private final GameSyncService gameSyncService;

    public OddsSyncTask(GameSyncService gameSyncService) {
        this.gameSyncService = gameSyncService;
    }

    // Now safely runs every 30 minutes in the background
    @Scheduled(initialDelay = 5000, fixedDelay = 1800000)
    public void syncOddsToDatabase() {
        System.out.println("Starting odds sync trigger...");

        // Let the perfected logic in GameSyncService handle the heavy lifting!
        gameSyncService.syncAllOdds();

        System.out.println("Odds sync trigger completed.");
    }
}