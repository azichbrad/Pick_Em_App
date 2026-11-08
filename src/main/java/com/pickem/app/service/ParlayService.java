package com.pickem.app.service;

import com.pickem.app.model.GroupParlayPick;
import com.pickem.app.model.Player;
import com.pickem.app.model.PlayerRecord;
import com.pickem.app.repository.GroupParlayPickRepository;
import com.pickem.app.repository.PlayerRecordRepository;
import com.pickem.app.repository.PickRepository; // 1. Import the PickRepository
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ParlayService {

    private final PlayerRecordRepository playerRecordRepo;
    private final GroupParlayPickRepository parlayPickRepo;
    private final PickRepository pickRepo; // 2. Add as a class field

    // 3. Add to constructor parameters and assign it
    public ParlayService(PlayerRecordRepository playerRecordRepo,
                         GroupParlayPickRepository parlayPickRepo,
                         PickRepository pickRepo) {
        this.playerRecordRepo = playerRecordRepo;
        this.parlayPickRepo = parlayPickRepo;
        this.pickRepo = pickRepo;
    }

    public String getParlayDutyNames(Long leagueId, int masterWeekNumber) {
        int ncaafEvalWeek = masterWeekNumber - 1;
        int nflEvalWeek = masterWeekNumber - 2;

        if (ncaafEvalWeek < 0) {
            return "Pending (Season just started)";
        }

        // Fetch the raw picks instead of the static records
        List<com.pickem.app.model.Pick> cfbPicks = pickRepo.findByLeagueIdAndWeekNumberAndSport(leagueId, ncaafEvalWeek, "NCAAF");
        List<com.pickem.app.model.Pick> nflPicks = new ArrayList<>();
        if (nflEvalWeek > 0) {
            nflPicks = pickRepo.findByLeagueIdAndWeekNumberAndSport(leagueId, nflEvalWeek, "NFL");
        }

        List<com.pickem.app.model.Pick> allPicks = new ArrayList<>();
        if (cfbPicks != null) allPicks.addAll(cfbPicks);
        if (nflPicks != null) allPicks.addAll(nflPicks);

        if (allPicks.isEmpty()) {
            return "Pending (No graded picks)";
        }

        // --- ADD THIS BLOCK ---
        // Prevent premature calculation by checking if any games are still in progress
        for (com.pickem.app.model.Pick pick : allPicks) {
            if ("PENDING".equalsIgnoreCase(pick.getStatus()) || "LOCKED".equalsIgnoreCase(pick.getStatus())) {
                return "Pending (Waiting on NFL)";
            }
        }

        Map<Player, Integer> totalWins = new HashMap<>();
        Map<Player, Integer> totalGames = new HashMap<>();

        // Aggregate wins directly from the graded picks
        for (com.pickem.app.model.Pick pick : allPicks) {
            if (pick.getPlayer() == null || pick.getStatus() == null) continue;

            Player player = pick.getPlayer();
            String status = pick.getStatus();

            if (status.equals("WIN") || status.equals("LOSS") || status.equals("PUSH")) {
                int gamesPlayed = totalGames.getOrDefault(player, 0) + 1;
                totalGames.put(player, gamesPlayed);

                if (status.equals("WIN")) {
                    int wins = totalWins.getOrDefault(player, 0) + 1;
                    totalWins.put(player, wins);
                } else if (!totalWins.containsKey(player)) {
                    totalWins.put(player, 0); // Ensure they are in the map even with 0 wins
                }
            }
        }

        double worstPercentage = 2.0;
        Map<Player, Double> winPercentages = new HashMap<>();

        for (Map.Entry<Player, Integer> entry : totalWins.entrySet()) {
            Player player = entry.getKey();
            int wins = entry.getValue();
            int games = totalGames.getOrDefault(player, 0);

            if (games > 0) {
                double winPct = (double) wins / games;
                winPercentages.put(player, winPct);

                if (winPct < worstPercentage) {
                    worstPercentage = winPct;
                }
            }
        }

        List<String> losers = new ArrayList<>();
        for (Map.Entry<Player, Double> entry : winPercentages.entrySet()) {
            if (Math.abs(entry.getValue() - worstPercentage) < 0.001) {
                losers.add(entry.getKey().getName());
            }
        }

        if (losers.isEmpty()) {
            return "Pending";
        } else if (losers.size() == 1) {
            return losers.get(0);
        } else if (losers.size() == 2) {
            return losers.get(0) + " & " + losers.get(1);
        } else {
            String allButLast = String.join(", ", losers.subList(0, losers.size() - 1));
            return allButLast + ", & " + losers.get(losers.size() - 1);
        }
    }

    public void saveOrUpdatePick(Player player, Long leagueId, int weekNumber, String sport, String gameId,
                                 String selection, Double lockedPoint, String matchName, String logoUrl) {
        GroupParlayPick pick = parlayPickRepo.findByLeagueIdAndWeekNumberAndPlayerId(leagueId, weekNumber, player.getId())
                .orElse(new GroupParlayPick());

        pick.setPlayer(player);
        pick.setLeagueId(leagueId); // Stamping the isolated league
        pick.setWeekNumber(weekNumber);
        pick.setSport(sport);
        pick.setGameId(gameId);
        pick.setSelection(selection);
        pick.setLockedPoint(lockedPoint);
        pick.setMatchName(matchName);
        pick.setLogoUrl(logoUrl);

        parlayPickRepo.save(pick);
    }

    public int getLockedPickCount(Long leagueId, int weekNumber) {
        return parlayPickRepo.countByLeagueIdAndWeekNumber(leagueId, weekNumber);
    }

    public List<GroupParlayPick> getPicksForWeek(Long leagueId, int weekNumber) {
        return parlayPickRepo.findByLeagueIdAndWeekNumber(leagueId, weekNumber);
    }
}