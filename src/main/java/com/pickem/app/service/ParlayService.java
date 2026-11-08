package com.pickem.app.service;

import com.pickem.app.model.GroupParlayPick;
import com.pickem.app.model.Player;
import com.pickem.app.model.PlayerRecord;
import com.pickem.app.repository.GroupParlayPickRepository;
import com.pickem.app.repository.PlayerRecordRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ParlayService {

    private final PlayerRecordRepository playerRecordRepo;
    private final GroupParlayPickRepository parlayPickRepo;

    public ParlayService(PlayerRecordRepository playerRecordRepo, GroupParlayPickRepository parlayPickRepo) {
        this.playerRecordRepo = playerRecordRepo;
        this.parlayPickRepo = parlayPickRepo;
    }

    public String getParlayDutyNames(Long leagueId, int currentWeekNumber) {
        int previousWeek = currentWeekNumber - 1;
        if (previousWeek < 1) {
            return "None (Week 1)";
        }

        List<PlayerRecord> lastWeekRecords = playerRecordRepo.findByLeagueIdAndWeekNumber(leagueId, previousWeek);
        if (lastWeekRecords.isEmpty()) {
            return "Pending";
        }

        Map<Player, Integer> totalWins = new HashMap<>();
        Map<Player, Integer> totalGames = new HashMap<>();

        for (PlayerRecord record : lastWeekRecords) {
            Player player = record.getPlayer();
            totalWins.merge(player, record.getWins(), Integer::sum);

            int gamesPlayed = record.getWins() + record.getLosses() + record.getPushes();
            totalGames.merge(player, gamesPlayed, Integer::sum);
        }

        double worstPercentage = 1.1;
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

        if (losers.size() == 1) {
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