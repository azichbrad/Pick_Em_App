package com.pickem.app.service.task;

import com.pickem.app.model.Pick;
import com.pickem.app.model.PlayerRecord;
import com.pickem.app.repository.PickRepository;
import com.pickem.app.repository.PlayerRecordRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RecordReconciliationTask {

    private final PickRepository pickRepo;
    private final PlayerRecordRepository playerRecordRepo;

    public RecordReconciliationTask(PickRepository pickRepo, PlayerRecordRepository playerRecordRepo) {
        this.pickRepo = pickRepo;
        this.playerRecordRepo = playerRecordRepo;
    }

    /**
     * Runs every day at 3:00 AM Pacific Time.
     * Cron format: Second Minute Hour Day Month Weekday
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Los_Angeles")
    @Transactional
    public void reconcileAllPlayerRecords() {
        System.out.println("Starting nightly player record reconciliation...");
        long startTime = System.currentTimeMillis();
        int updatedCount = 0;
        int createdCount = 0;

        // 1. Fetch all picks and group them by League-Player-Sport-Week
        List<Pick> allPicks = pickRepo.findAll();
        Map<String, List<Pick>> groupedPicks = allPicks.stream()
                .filter(p -> p.getLeagueId() != null && p.getPlayer() != null && p.getSport() != null && p.getWeekNumber() != null)
                .collect(Collectors.groupingBy(p ->
                        p.getLeagueId() + "-" + p.getPlayer().getId() + "-" + p.getSport() + "-" + p.getWeekNumber()
                ));

        // 2. Fetch all existing records and map them by the exact same key for O(1) lookups
        List<PlayerRecord> allRecords = playerRecordRepo.findAll();
        Map<String, PlayerRecord> recordMap = allRecords.stream()
                .filter(r -> r.getLeagueId() != null && r.getPlayer() != null && r.getSport() != null && r.getWeekNumber() != null)
                .collect(Collectors.toMap(
                        r -> r.getLeagueId() + "-" + r.getPlayer().getId() + "-" + r.getSport() + "-" + r.getWeekNumber(),
                        r -> r,
                        (r1, r2) -> r1 // safely handle duplicates if they accidentally exist
                ));

        // 3. Evaluate the true grading for every group of picks
        for (Map.Entry<String, List<Pick>> entry : groupedPicks.entrySet()) {
            String groupKey = entry.getKey();
            List<Pick> picks = entry.getValue();

            Pick referencePick = picks.get(0);
            Long leagueId = referencePick.getLeagueId();
            String sport = referencePick.getSport();
            Integer week = referencePick.getWeekNumber();

            int trueWins = 0;
            int trueLosses = 0;
            int truePushes = 0;

            for (Pick pick : picks) {
                if ("WIN".equalsIgnoreCase(pick.getStatus())) trueWins++;
                else if ("LOSS".equalsIgnoreCase(pick.getStatus())) trueLosses++;
                else if ("PUSH".equalsIgnoreCase(pick.getStatus())) truePushes++;
            }

            // If there are zero graded picks for this week, we don't need to force a record creation
            if (trueWins == 0 && trueLosses == 0 && truePushes == 0) {
                continue;
            }

            PlayerRecord existingRecord = recordMap.get(groupKey);

            // 4. Create missing records OR update mismatched records
            if (existingRecord == null) {
                PlayerRecord newRecord = new PlayerRecord(referencePick.getPlayer(), sport, week, leagueId);
                newRecord.setWins(trueWins);
                newRecord.setLosses(trueLosses);
                newRecord.setPushes(truePushes);
                playerRecordRepo.save(newRecord);
                createdCount++;
                System.out.printf("Created missing record for %s (Week %d %s): %dW-%dL-%dP%n",
                        referencePick.getPlayer().getName(), week, sport, trueWins, trueLosses, truePushes);
            } else {
                if (existingRecord.getWins() != trueWins ||
                        existingRecord.getLosses() != trueLosses ||
                        existingRecord.getPushes() != truePushes) {

                    System.out.printf("Mismatch found for %s (Week %d %s). Old: %dW-%dL, True: %dW-%dL%n",
                            existingRecord.getPlayer().getName(), week, sport, existingRecord.getWins(), existingRecord.getLosses(), trueWins, trueLosses);

                    existingRecord.setWins(trueWins);
                    existingRecord.setLosses(trueLosses);
                    existingRecord.setPushes(truePushes);
                    playerRecordRepo.save(existingRecord);
                    updatedCount++;
                }
            }
        }

        System.out.println("Reconciliation complete in " + (System.currentTimeMillis() - startTime) +
                "ms. Records created: " + createdCount + ", updated: " + updatedCount);
    }
}