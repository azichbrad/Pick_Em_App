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

@Service
public class ScoreAndGradingService {

    private final OddsService oddsService;
    private final GameRepository gameRepository;
    private final PickRepository pickRepository;
    private final PlayerRecordRepository playerRecordRepo;

    public ScoreAndGradingService(OddsService oddsService, GameRepository gameRepository, PickRepository pickRepository, PlayerRecordRepository playerRecordRepo) {
        this.oddsService = oddsService;
        this.gameRepository = gameRepository;
        this.pickRepository = pickRepository;
        this.playerRecordRepo = playerRecordRepo;
    }

    // UPDATED: Runs at the top of every hour to stay fresh without hitting rate limits
    @Scheduled(cron = "0 0 * * * *")
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
            // Only process games that are fully completed and possess at least 2 scores
            if (Boolean.TRUE.equals(apiScore.completed()) && apiScore.scores() != null && apiScore.scores().size() >= 2) {

                for (Game dbGame : pendingGames) {
                    // FIX 1: Check if both teams match, regardless of who the API says is "Home" or "Away"
                    boolean homeInApi = isTeamMatch(dbGame.getHomeTeam(), apiScore.homeTeam()) || isTeamMatch(dbGame.getHomeTeam(), apiScore.awayTeam());
                    boolean awayInApi = isTeamMatch(dbGame.getAwayTeam(), apiScore.homeTeam()) || isTeamMatch(dbGame.getAwayTeam(), apiScore.awayTeam());

                    if (homeInApi && awayInApi) {
                        Integer dbHomeScore = null;
                        Integer dbAwayScore = null;

                        // FIX 2: Map the API scores directly to the DB teams to avoid swapped Home/Away bugs
                        for (ScoreDTO.TeamScoreDTO ts : apiScore.scores()) {
                            try {
                                // FIX 3: Parse as double first to prevent NumberFormatExceptions if the API sends "34.0"
                                int parsedScore = (int) Double.parseDouble(ts.score()); 
                                
                                if (isTeamMatch(ts.name(), dbGame.getHomeTeam())) {
                                    dbHomeScore = parsedScore;
                                } else if (isTeamMatch(ts.name(), dbGame.getAwayTeam())) {
                                    dbAwayScore = parsedScore;
                                }
                            } catch (Exception ignored) {}
                        }

                        if (dbHomeScore != null && dbAwayScore != null) {
                            dbGame.setHomeScore(dbHomeScore);
                            dbGame.setAwayScore(dbAwayScore);
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

        for (Pick pick : picks) {
            String newStatus = "PENDING";

            if ("spread".equalsIgnoreCase(pick.getMarketType())) {
                double spread = pick.getLockedPoint();

                // FIX 4: Added .trim().equalsIgnoreCase() to prevent spacing mismatch issues
                if (pick.getSelectionSide().trim().equalsIgnoreCase(game.getHomeTeam().trim())) {
                    double adjustedHome = game.getHomeScore() + spread;
                    if (adjustedHome > game.getAwayScore()) newStatus = "WIN";
                    else if (adjustedHome < game.getAwayScore()) newStatus = "LOSS";
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

                if ("Over".equalsIgnoreCase(pick.getSelectionSide().trim())) {
                    if (totalScore > lockedTotal) newStatus = "WIN";
                    else if (totalScore < lockedTotal) newStatus = "LOSS";
                    else newStatus = "PUSH";
                } else if ("Under".equalsIgnoreCase(pick.getSelectionSide().trim())) {
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

    private void updatePlayerRecords(Player player, String sport, int weekNumber) {
        List<Pick> allSportPicks = pickRepository.findByPlayerIdAndSport(player.getId(), sport);

        int overallWins = 0, overallLosses = 0, overallPushes = 0;
        int weeklyWins = 0, weeklyLosses = 0, weeklyPushes = 0;

        for (Pick p : allSportPicks) {
            boolean isWin = "WIN".equals(p.getStatus());
            boolean isLoss = "LOSS".equals(p.getStatus());
            boolean isPush = "PUSH".equals(p.getStatus());

            if (isWin) overallWins++;
            if (isLoss) overallLosses++;
            if (isPush) overallPushes++;

            if (p.getWeekNumber() != null && p.getWeekNumber() == weekNumber) {
                if (isWin) weeklyWins++;
                if (isLoss) weeklyLosses++;
                if (isPush) weeklyPushes++;
            }
        }

        PlayerRecord weeklyRecord = playerRecordRepo.findByPlayerIdAndSportAndWeekNumber(player.getId(), sport, weekNumber)
                .orElse(new PlayerRecord(player, sport, weekNumber));
        weeklyRecord.setWins(weeklyWins);
        weeklyRecord.setLosses(weeklyLosses);
        weeklyRecord.setPushes(weeklyPushes);
        playerRecordRepo.save(weeklyRecord);

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
