package com.pickem.app.service;

import com.pickem.app.dto.ScoreDTO;
import com.pickem.app.model.Game;
import com.pickem.app.model.Pick;
import com.pickem.app.model.Player;
import com.pickem.app.model.PlayerRecord;
import com.pickem.app.repository.GameRepository;
import com.pickem.app.repository.PickRepository;
import com.pickem.app.repository.PlayerRecordRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Service
public class ScoreAndGradingService {

    private final OddsService oddsService;
    private final GameRepository gameRepository;
    private final PickRepository pickRepository;
    private final PlayerRecordRepository playerRecordRepo; // NEW: Added to update standings

    public ScoreAndGradingService(OddsService oddsService, GameRepository gameRepository, PickRepository pickRepository, PlayerRecordRepository playerRecordRepo) {
        this.oddsService = oddsService;
        this.gameRepository = gameRepository;
        this.pickRepository = pickRepository;
        this.playerRecordRepo = playerRecordRepo;
    }

    // Runs every 30 minutes to check for final scores and grade picks
    @Scheduled(fixedDelay = 1800000)
    public void syncScoresAndGrade() {
        System.out.println("Starting score sync and grading engine...");

        List<ScoreDTO> ncaafScores = oddsService.getCompletedScores("americanfootball_ncaaf");
        List<ScoreDTO> nflScores = oddsService.getCompletedScores("americanfootball_nfl");

        processScores(ncaafScores);
        processScores(nflScores);

        System.out.println("Score sync and grading completed.");
    }

    private void processScores(List<ScoreDTO> apiScores) {
        if (apiScores == null || apiScores.isEmpty()) return;

        List<Game> pendingGames = gameRepository.findByCompletedFalse();

        for (ScoreDTO apiScore : apiScores) {
            // Only process games that are fully completed and possess a dual-score array
            if (Boolean.TRUE.equals(apiScore.completed()) && apiScore.scores() != null && apiScore.scores().size() == 2) {

                for (Game dbGame : pendingGames) {
                    // Match by partial name since SharpAPI strings differ slightly from OddsAPI strings
                    if (isTeamMatch(dbGame.getHomeTeam(), apiScore.homeTeam()) &&
                            isTeamMatch(dbGame.getAwayTeam(), apiScore.awayTeam())) {

                        Integer homeScore = null;
                        Integer awayScore = null;

                        for (ScoreDTO.TeamScoreDTO ts : apiScore.scores()) {
                            try {
                                if (ts.name().equals(apiScore.homeTeam())) {
                                    homeScore = Integer.parseInt(ts.score());
                                } else if (ts.name().equals(apiScore.awayTeam())) {
                                    awayScore = Integer.parseInt(ts.score());
                                }
                            } catch (NumberFormatException ignored) {}
                        }

                        if (homeScore != null && awayScore != null) {
                            dbGame.setHomeScore(homeScore);
                            dbGame.setAwayScore(awayScore);
                            dbGame.setCompleted(true);
                            gameRepository.save(dbGame);

                            // Trigger the grading math!
                            gradePicksForGame(dbGame);
                        }
                    }
                }
            }
        }
    }

    @Transactional
    public void gradePicksForGame(Game game) {
        List<Pick> picks = pickRepository.findByGameIdAndStatus(game.getId(), "PENDING");
        Set<String> playersToUpdate = new HashSet<>();

        for (Pick pick : picks) {
            String newStatus = "PENDING";

            if ("spread".equalsIgnoreCase(pick.getMarketType())) {
                double spread = pick.getLockedPoint();

                if (pick.getSelectionSide().equals(game.getHomeTeam())) {
                    double adjustedHome = game.getHomeScore() + spread;
                    if (adjustedHome > game.getAwayScore()) newStatus = "WIN"; // FIXED: Changed WON to WIN
                    else if (adjustedHome < game.getAwayScore()) newStatus = "LOSS"; // FIXED: Changed LOST to LOSS
                    else newStatus = "PUSH";
                } else {
                    double adjustedAway = game.getAwayScore() + spread;
                    if (adjustedAway > game.getHomeScore()) newStatus = "WIN";
                    else if (adjustedAway < game.getHomeScore()) newStatus = "LOSS";
                    else newStatus = "PUSH";
                }
            }
            else if ("total".equalsIgnoreCase(pick.getMarketType())) {
                double totalScore = game.getHomeScore() + game.getAwayScore();
                double lockedTotal = pick.getLockedPoint();

                if ("Over".equalsIgnoreCase(pick.getSelectionSide())) {
                    if (totalScore > lockedTotal) newStatus = "WIN";
                    else if (totalScore < lockedTotal) newStatus = "LOSS";
                    else newStatus = "PUSH";
                } else if ("Under".equalsIgnoreCase(pick.getSelectionSide())) {
                    if (totalScore < lockedTotal) newStatus = "WIN";
                    else if (totalScore > lockedTotal) newStatus = "LOSS";
                    else newStatus = "PUSH";
                }
            }

            pick.setStatus(newStatus);
            pickRepository.save(pick);

            // Track the player and week to recalculate records
            if (pick.getPlayer() != null && pick.getWeekNumber() != null) {
                updatePlayerRecords(pick.getPlayer(), game.getSport(), pick.getWeekNumber());
            }
        }
    }

    // NEW: Automatically calculates Weekly and Overall records so the Leaderboard updates instantly
    private void updatePlayerRecords(Player player, String sport, int weekNumber) {
        List<Pick> allSportPicks = pickRepository.findByPlayerIdAndSport(player.getId(), sport);

        int overallWins = 0, overallLosses = 0, overallPushes = 0;
        int weeklyWins = 0, weeklyLosses = 0, weeklyPushes = 0;

        for (Pick p : allSportPicks) {
            boolean isWin = "WIN".equals(p.getStatus());
            boolean isLoss = "LOSS".equals(p.getStatus());
            boolean isPush = "PUSH".equals(p.getStatus());

            // Add to overall records
            if (isWin) overallWins++;
            if (isLoss) overallLosses++;
            if (isPush) overallPushes++;

            // Add to weekly records if the week matches
            if (p.getWeekNumber() != null && p.getWeekNumber() == weekNumber) {
                if (isWin) weeklyWins++;
                if (isLoss) weeklyLosses++;
                if (isPush) weeklyPushes++;
            }
        }

        // 1. Save Weekly Record
        PlayerRecord weeklyRecord = playerRecordRepo.findByPlayerIdAndSportAndWeekNumber(player.getId(), sport, weekNumber)
                .orElse(new PlayerRecord(player, sport, weekNumber));
        weeklyRecord.setWins(weeklyWins);
        weeklyRecord.setLosses(weeklyLosses);
        weeklyRecord.setPushes(weeklyPushes);
        playerRecordRepo.save(weeklyRecord);

        // 2. Save Overall Record (Stored as Week 0)
        PlayerRecord overallRecord = playerRecordRepo.findByPlayerIdAndSportAndWeekNumber(player.getId(), sport, 0)
                .orElse(new PlayerRecord(player, sport, 0));
        overallRecord.setWins(overallWins);
        overallRecord.setLosses(overallLosses);
        overallRecord.setPushes(overallPushes);
        playerRecordRepo.save(overallRecord);
    }

    private boolean isTeamMatch(String dbTeam, String apiTeam) {
        if (dbTeam == null || apiTeam == null) return false;
        return dbTeam.toLowerCase().contains(apiTeam.toLowerCase()) ||
                apiTeam.toLowerCase().contains(dbTeam.toLowerCase());
    }
}