package com.pickem.app.service.task;

import com.pickem.app.service.api.CfbdSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class AnalyticsSyncTask {

    private final CfbdSyncService cfbdSyncService;

    public AnalyticsSyncTask(CfbdSyncService cfbdSyncService) {
        this.cfbdSyncService = cfbdSyncService;
    }

    /**
     * Runs every day at 4:00 AM Pacific Time, safely after your
     * 3:00 AM RecordReconciliationTask finishes.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "America/Los_Angeles")
    public void syncWeeklyAnalytics() {
        System.out.println("Starting scheduled CFBD analytics sync...");

        LocalDate today = LocalDate.now(ZoneId.of("America/Los_Angeles"));
        int currentMonth = today.getMonthValue();

        // Only trigger the API during the college football season (August through January)
        if (currentMonth >= 8 || currentMonth == 1) {
            int seasonYear = today.getYear();

            // If it's January (National Championship / Bowl Games), the stats technically
            // belong to the previous year's season data in CFBD.
            if (currentMonth == 1) {
                seasonYear--;
            }

            try {
                cfbdSyncService.syncSeasonAdvancedStats(seasonYear);
            } catch (Exception e) {
                System.err.println("Scheduled analytics sync failed: " + e.getMessage());
            }
        }
    }
}