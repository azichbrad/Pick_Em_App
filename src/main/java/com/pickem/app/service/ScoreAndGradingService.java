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

    @PostConstruct
    public void forceRunOnStartup() {
        System.out.println("Waking up! Running an immediate score sync...");
        syncScoresAndGrade();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void syncScoresAndGrade() {
        System.out.println("Starting score sync and grading engine...");

        // Look back 10 days so all completed games from the previous week are caught
        List<ScoreDTO> ncaafScores = oddsService.getCompletedScores("americanfootball_ncaaf", 10);
        List<ScoreDTO> nflScores = oddsService.getCompletedScores("americanfootball_nfl", 10);

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
            if (Boolean.TRUE.equals(apiScore.completed()) && apiScore.scores() != null && apiScore.scores().size() >= 2) {
                for (Game dbGame : pendingGames) {
                    // Check if both teams match (accounting for neutral-site swaps)
                    boolean directMatch = isTeamMatch(dbGame.getHomeTeam(), apiScore.homeTeam()) &&
                            isTeamMatch(dbGame.getAwayTeam(), apiScore.awayTeam());

                    boolean swappedMatch = isTeamMatch(dbGame.getHomeTeam(), apiScore.awayTeam()) &&
                            isTeamMatch(dbGame.getAwayTeam(), apiScore.homeTeam());

                    if (directMatch || swappedMatch) {
                        System.out.println("🎯 MATCH FOUND IN DB FOR: " + dbGame.getAwayTeam() + " @ " + dbGame.getHomeTeam());
                        Integer dbHomeScore = null;
                        Integer dbAwayScore = null;

                        for (ScoreDTO.TeamScoreDTO ts : apiScore.scores()) {
                            try {
                                int parsedScore = (int) Double.parseDouble(ts.score());

                                if (isTeamMatch(dbGame.getHomeTeam(), ts.name())) {
                                    dbHomeScore = parsedScore;
                                } else if (isTeamMatch(dbGame.getAwayTeam(), ts.name())) {
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

    // Bidirectional normalizer and fuzzy matcher
    private boolean isTeamMatch(String dbTeam, String apiTeam) {
        if (dbTeam == null || apiTeam == null) return false;

        String normDb = normalizeTeam(dbTeam);
        String normApi = normalizeTeam(apiTeam);

        if (normDb.equals(normApi)) return true;
        if (!normDb.isEmpty() && normApi.contains(normDb)) return true;
        if (!normApi.isEmpty() && normDb.contains(normApi)) return true;

        String cleanDb = dbTeam.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
        String cleanApi = apiTeam.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
        return cleanApi.contains(cleanDb) || cleanDb.contains(cleanApi);
    }

    private String normalizeTeam(String name) {
        if (name == null) return "";
        String s = name.toLowerCase().trim()
                .replaceAll("&", "and")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Collegiate aliases & abbreviation bridges
        if (s.equals("lsu") || s.contains("louisiana state")) return "lsu";
        if (s.equals("ole miss") || s.equals("mississippi rebels") || s.equals("mississippi")) return "ole miss";
        if (s.equals("uconn") || s.contains("connecticut")) return "uconn";
        if (s.equals("liu") || s.contains("long island")) return "liu";
        if (s.equals("umass") || s.contains("massachusetts")) return "umass";
        if (s.equals("appalachian state") || s.contains("app state")) return "app state";
        if (s.equals("nc state") || s.contains("north carolina state")) return "nc state";
        if (s.equals("ul monroe") || s.equals("ulm") || s.contains("louisiana monroe")) return "ul monroe";
        if (s.equals("fiu") || s.contains("florida international")) return "fiu";
        if (s.equals("fau") || s.contains("florida atlantic")) return "fau";
        if (s.equals("miami fl") || s.contains("miami hurricanes")) return "miami fl";
        if (s.equals("miami oh") || s.equals("miami ohio") || s.contains("miami oh redhawks")) return "miami oh";
        if (s.contains("hawaii")) return "hawaii";
        if (s.equals("pitt") || s.contains("pittsburgh")) return "pittsburgh";
        if (s.equals("usc") || s.contains("southern california")) return "usc";

        return s;
    }
}