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

    // 1. LIGHTWEIGHT HOURLY SYNC: Increased lookback to 4 days to catch API delays
    @Scheduled(cron = "0 0 * * * *")
    public void hourlyActiveSync() {
        System.out.println("Starting lightweight hourly score sync...");

        // 4-day lookback ensures weekend stragglers are caught without hitting limits
        processScores(oddsService.getCompletedScores("americanfootball_ncaaf", 4));
        processScores(oddsService.getCompletedScores("americanfootball_nfl", 4));

        System.out.println("Hourly sync completed.");
    }

    // 2. DEEP NIGHTLY SYNC: Anchored to Pacific Time so it actually runs at 2:00 AM locally
    @Scheduled(cron = "0 0 2 * * *", zone = "America/Los_Angeles")
    public void nightlyDeepSync() {
        System.out.println("Starting heavy nightly deep score sync...");

        // 10-day lookback to catch weird API delays, only costs limits once a day!
        processScores(oddsService.getCompletedScores("americanfootball_ncaaf", 10));
        processScores(oddsService.getCompletedScores("americanfootball_nfl", 10));

        System.out.println("Nightly deep sync completed.");
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

                    // 🛑 GUARDRAIL 1: Ensure the sport matches exactly (prevents College "Eagles" grading NFL "Eagles")
                    if (apiScore.sport_key() != null && dbGame.getSport() != null && !apiScore.sport_key().equalsIgnoreCase(dbGame.getSport())) {
                        continue;
                    }

                    // 🛑 GUARDRAIL 2: Never accept final scores for games that haven't kicked off yet
                    if (dbGame.getCommenceTime() != null && dbGame.getCommenceTime().isAfter(java.time.Instant.now())) {
                        continue;
                    }

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

        int weeklyWins = 0, weeklyLosses = 0, weeklyPushes = 0;

        for (Pick p : allSportPicks) {
            boolean isWin = "WIN".equals(p.getStatus());
            boolean isLoss = "LOSS".equals(p.getStatus());
            boolean isPush = "PUSH".equals(p.getStatus());

            if (p.getWeekNumber() != null && p.getWeekNumber() == weekNumber) {
                if (isWin) weeklyWins++;
                if (isLoss) weeklyLosses++;
                if (isPush) weeklyPushes++;
            }
        }

        // Only save the individual weekly record.
        // Do NOT save an overall record to week_number = 0, as the UI already sums the weekly rows.
        PlayerRecord weeklyRecord = playerRecordRepo.findByPlayerIdAndSportAndWeekNumber(player.getId(), sport, weekNumber)
                .orElse(new PlayerRecord(player, sport, weekNumber));

        weeklyRecord.setWins(weeklyWins);
        weeklyRecord.setLosses(weeklyLosses);
        weeklyRecord.setPushes(weeklyPushes);
        playerRecordRepo.save(weeklyRecord);
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

        // NFL aliases & city/franchise bridges
        if (s.contains("cardinals") || s.equals("ari")) return "arizona cardinals";
        if (s.contains("falcons") || s.equals("atl")) return "atlanta falcons";
        if (s.contains("ravens") || s.equals("bal")) return "baltimore ravens";
        if (s.contains("bills") || s.equals("buf")) return "buffalo bills";
        if (s.contains("panthers") || s.equals("car")) return "carolina panthers";
        if (s.contains("bears") || s.equals("chi")) return "chicago bears";
        if (s.contains("bengals") || s.equals("cin")) return "cincinnati bengals";
        if (s.contains("browns") || s.equals("cle")) return "cleveland browns";
        if (s.contains("cowboys") || s.equals("dal")) return "dallas cowboys";
        if (s.contains("broncos") || s.equals("den")) return "denver broncos";
        if (s.contains("lions") || s.equals("det")) return "detroit lions";
        if (s.contains("packers") || s.equals("gb")) return "green bay packers";
        if (s.contains("texans") || s.equals("hou")) return "houston texans";
        if (s.contains("colts") || s.equals("ind")) return "indianapolis colts";
        if (s.contains("jaguars") || s.equals("jax")) return "jacksonville jaguars";
        if (s.contains("chiefs") || s.equals("kc")) return "kansas city chiefs";
        if (s.contains("raiders") || s.equals("lv")) return "las vegas raiders";
        if (s.contains("chargers") || s.equals("lac")) return "los angeles chargers";
        if (s.contains("rams") || s.equals("lar")) return "los angeles rams";
        if (s.contains("dolphins") || s.equals("mia")) return "miami dolphins";
        if (s.contains("vikings") || s.equals("min")) return "minnesota vikings";
        if (s.contains("patriots") || s.equals("ne")) return "new england patriots";
        if (s.contains("saints") || s.equals("no")) return "new orleans saints";
        if (s.contains("giants") || s.equals("nyg")) return "new york giants";
        if (s.contains("jets") || s.equals("nyj")) return "new york jets";
        if (s.contains("eagles") || s.equals("phi")) return "philadelphia eagles";
        if (s.contains("steelers") || s.equals("pit")) return "pittsburgh steelers";
        if (s.contains("49ers") || s.contains("niners") || s.equals("sf")) return "san francisco 49ers";
        if (s.contains("seahawks") || s.equals("sea")) return "seattle seahawks";
        if (s.contains("buccaneers") || s.contains("bucs") || s.equals("tb")) return "tampa bay buccaneers";
        if (s.contains("titans") || s.equals("ten")) return "tennessee titans";
        if (s.contains("commanders") || s.equals("was")) return "washington commanders";

        return s;
    }
}