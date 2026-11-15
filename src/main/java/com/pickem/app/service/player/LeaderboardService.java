package com.pickem.app.service.player;

import com.pickem.app.dto.LeaderboardEntryDTO;
import com.pickem.app.model.Player;
import com.pickem.app.model.PlayerRecord;
import com.pickem.app.repository.PlayerRecordRepository;
import com.pickem.app.repository.PlayerRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LeaderboardService {

    private final PlayerRecordRepository playerRecordRepo;
    private final PlayerRepository playerRepo;

    public LeaderboardService(PlayerRecordRepository playerRecordRepo, PlayerRepository playerRepo) {
        this.playerRecordRepo = playerRecordRepo;
        this.playerRepo = playerRepo;
    }

    /**
     * Generates a leaderboard for a specific league and sport.
     * Use sport = "ALL" to combine NCAAF and NFL records.
     */
    public List<LeaderboardEntryDTO> generateLeaderboard(Long leagueId, String sport, double unitStake) {
        List<LeaderboardEntryDTO> leaderboard = new ArrayList<>();

        // Fetch all players but filter out the test account
        List<Player> players = playerRepo.findAll().stream()
                .filter(player -> !"Mallard Scallops".equalsIgnoreCase(player.getName()))
                .toList();

        for (Player player : players) {
            int totalWins = 0;
            int totalLosses = 0;
            int totalPushes = 0;
            List<PlayerRecord> records;

            // Fetch ALL weeks and aggregate them
            if ("ALL".equalsIgnoreCase(sport)) {
                records = playerRecordRepo.findByLeagueIdAndPlayerId(leagueId, player.getId());
            } else {
                records = playerRecordRepo.findByLeagueIdAndPlayerIdAndSport(leagueId, player.getId(), sport);
            }

            for (PlayerRecord pr : records) {
                totalWins += pr.getWins();
                totalLosses += pr.getLosses();
                totalPushes += pr.getPushes();
            }

            // Only add players who have actually made picks
            if (totalWins > 0 || totalLosses > 0 || totalPushes > 0) {
                leaderboard.add(calculateBettingStats(player, totalWins, totalLosses, totalPushes, unitStake));
            }
        }

        // Sort by most wins, then fewest losses
        leaderboard.sort((a, b) -> {
            if (b.wins() != a.wins()) return Integer.compare(b.wins(), a.wins());
            return Integer.compare(a.losses(), b.losses());
        });

        return leaderboard;
    }

    private LeaderboardEntryDTO calculateBettingStats(Player player, int wins, int losses, int pushes, double unitStake) {
        int totalGraded = wins + losses;
        double winPct = totalGraded == 0 ? 0.0 : ((double) wins / totalGraded) * 100.0;

        // Vegas -110 Odds Math
        double payoutPerWin = unitStake * (100.0 / 110.0);
        double totalProfit = (wins * payoutPerWin) - (losses * unitStake);

        // ROI Calculation (Total Profit / Total Risked)
        double totalRisked = totalGraded * unitStake;
        double roi = totalRisked == 0 ? 0.0 : (totalProfit / totalRisked) * 100.0;

        String aiTag = generateAiTag(winPct, totalGraded);

        return new LeaderboardEntryDTO(
                player.getId(),
                player.getName(),
                wins,
                losses,
                pushes,
                winPct,
                totalProfit,
                roi,
                aiTag
        );
    }

    private String generateAiTag(double winPct, int totalGraded) {
        if (totalGraded < 5) return "🌱 Rookie Scouter (Need more picks)";

        if (winPct >= 60.0) return "👑 Vegas VIP (Absolute domination)";
        if (winPct >= 52.4) return "🔥 Sharp Bettor (Beating the Vig!)";
        if (winPct >= 50.0) return "🛡️ Treading Water (Almost profitable)";
        if (winPct >= 40.0) return "❄️ Chasing Steam (Fading your picks)";
        return "💀 The Cooler (Certified Mush)";
    }
}