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
import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Map;

@Service
public class ScoreAndGradingService {

    private final OddsService oddsService;
    private final GameRepository gameRepository;
    private final PickRepository pickRepository;
    private final PlayerRecordRepository playerRecordRepo;

    // Bridge common naming variations between SharpAPI and score APIs (CFBD / Highlightly)
    private static final Map<String, String> TEAM_ALIASES = Map.of(
            "miami fl", "miami",
            "southern california", "usc",
            "ole miss", "mississippi",
            "lsu", "louisiana state",
            "pitt", "pittsburgh"
    );

    public ScoreAndGradingService(OddsService oddsService, GameRepository gameRepository, PickRepository pickRepository, PlayerRecordRepository playerRecordRepo) {
        this.oddsService = oddsService;
        this.gameRepository = gameRepository;
        this.pickRepository = pickRepository;
        this.playerRecordRepo = playerRecordRepo;
    }

    @PostConstruct
    public void forceRunOnStartup() {
        System.out.println("Waking up! Running an immediate score sync...");
        syncScoresAndGrade();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void syncScoresAndGrade() {
        System.out.println("Starting score sync and grading engine...");

        List<ScoreDTO> ncaafScores = oddsService.getCompletedScores("americanfootball_ncaaf", 3);
        List<ScoreDTO> nflScores = oddsService.getCompletedScores("americanfootball_nfl", 3);

        processScores(ncaafScores);
        processScores(nflScores);

        System.out.println("Score sync and grading completed.");
    }

    private void processScores(List<ScoreDTO> apiScores) {
        if (apiScores == null || apiScores.isEmpty()) {
            System.out.println("⚠️ WARNING: processScores received ZERO scores from OddsService!");
            return;
        }

        System.out.println("🔍 Processing " + apiScores.size() + " incoming API scores...");
        List<Game> pendingGames = gameRepository.findByCompletedFalse();

        for (ScoreDTO apiScore : apiScores) {
            System.out.println("-> Checking API Game: " + apiScore.homeTeam() + " vs " + apiScore.awayTeam() + " [Completed: " + apiScore.completed() + "]");

            if (Boolean.TRUE.equals(apiScore.completed()) && apiScore.scores() != null && apiScore.scores().size() >= 2) {
                for (Game dbGame : pendingGames) {
                    boolean homeInApi = isTeamMatch(dbGame.getHomeTeam(), apiScore.homeTeam()) || isTeamMatch(dbGame.getHomeTeam(), apiScore.awayTeam());
                    boolean awayInApi = isTeamMatch(dbGame.getAwayTeam(), apiScore.homeTeam()) || isTeamMatch(dbGame.getAwayTeam(), apiScore.awayTeam());

                    if (homeInApi && awayInApi) {
                        System.out.println(" MATCH FOUND IN DB FOR: " + dbGame.getHomeTeam() + " vs " + dbGame.getAwayTeam());
                        Integer dbHomeScore = null;
                        Integer dbAwayScore = null;

                        for (ScoreDTO.TeamScoreDTO ts : apiScore.scores()) {
                            try {
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

                            System.out.println("✅ Successfully graded & updated game: " + dbGame.getAwayTeam() + " (" + dbAwayScore + ") @ " + dbGame.getHomeTeam() + " (" + dbHomeScore + ")");

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

    private boolean isTeamMatch(String team1, String team2) {
        if (team1 == null || team2 == null) return false;

        String t1 = team1.trim().toLowerCase();
        String t2 = team2.trim().toLowerCase();

        // Apply alias translation if present
        t1 = TEAM_ALIASES.getOrDefault(t1, t1);
        t2 = TEAM_ALIASES.getOrDefault(t2, t2);

        return t1.contains(t2) || t2.contains(t1);
    }
}