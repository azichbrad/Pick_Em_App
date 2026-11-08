package com.pickem.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pickem.app.model.Game;
import com.pickem.app.model.Pick;
import com.pickem.app.model.Player;
import com.pickem.app.model.PlayerRecord;
import com.pickem.app.repository.GameRepository;
import com.pickem.app.repository.PickRepository;
import com.pickem.app.repository.PlayerRecordRepository;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

@Service
public class ScoreAndGradingService {

    private final GameRepository gameRepository;
    private final PickRepository pickRepository;
    private final PlayerRecordRepository playerRecordRepo;
    private final RestTemplate restTemplate;

    public ScoreAndGradingService(GameRepository gameRepository,
                                  PickRepository pickRepository,
                                  PlayerRecordRepository playerRecordRepo,
                                  RestTemplateBuilder restTemplateBuilder) {
        this.gameRepository = gameRepository;
        this.pickRepository = pickRepository;
        this.playerRecordRepo = playerRecordRepo;
        this.restTemplate = restTemplateBuilder.build();
    }

    // 1. ACTIVE GAMEDAY SYNC: Runs every hour on the hour during game days
    @Scheduled(cron = "0 0 * * * THU,SAT,SUN,MON", zone = "America/Los_Angeles")
    public void hourlyActiveSync() {
        System.out.println("Starting active gameday ESPN score sync...");
        processEspnScores(fetchEspnScores("NCAAF"), "NCAAF");
        processEspnScores(fetchEspnScores("NFL"), "NFL");
        System.out.println("Active gameday ESPN sync completed.");
    }

    // 2. OFF-DAY SYNC: Runs every 6 hours (Midnight, 6 AM, Noon, 6 PM) to catch late corrections
    @Scheduled(cron = "0 0 */6 * * TUE,WED,FRI", zone = "America/Los_Angeles")
    public void offDaySync() {
        System.out.println("Starting off-day ESPN score sync...");
        processEspnScores(fetchEspnScores("NCAAF"), "NCAAF");
        processEspnScores(fetchEspnScores("NFL"), "NFL");
        System.out.println("Off-day ESPN sync completed.");
    }

    // @Scheduled(cron = "0 0 2 * * TUE,WED", zone = "America/Los_Angeles")
    public void nightlyDeepSync() {
        System.out.println("Starting heavy nightly deep ESPN score sync...");
        processEspnScores(fetchEspnScores("NCAAF"), "NCAAF");
        processEspnScores(fetchEspnScores("NFL"), "NFL");
        System.out.println("Nightly deep ESPN sync completed.");
    }

    // 1. The ESPN Scraper
    private List<JsonNode> fetchEspnScores(String sport) {
        List<JsonNode> completedGames = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("America/Los_Angeles"));

        for (int i = 0; i <= 4; i++) {
            String dateStr = today.minusDays(i).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

            // Reverted back to groups=80 to fix the 400 Bad Request
            String url = "NFL".equalsIgnoreCase(sport)
                    ? "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?dates=" + dateStr
                    : "https://site.api.espn.com/apis/site/v2/sports/football/college-football/scoreboard?groups=80&limit=300&dates=" + dateStr;

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                headers.set("Accept", "application/json");

                HttpEntity<String> entity = new HttpEntity<>(headers);
                ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode events = response.getBody().path("events");
                    for (JsonNode event : events) {
                        JsonNode statusNode = event.path("status").path("type");

                        // Check the boolean instead of the string to catch "STATUS_FINAL_OT"
                        boolean isCompleted = statusNode.path("completed").asBoolean(false);
                        if (isCompleted) {
                            completedGames.add(event);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to fetch ESPN scores for " + sport + " on " + dateStr + ": " + e.getMessage());
            }
        }
        return completedGames;
    }

    // 2. The JSON Parser & Matcher
    private void processEspnScores(List<JsonNode> espnGames, String sport) {
        if (espnGames.isEmpty()) {
            System.out.println("⚠️ WARNING: No completed ESPN games found for " + sport);
            return;
        }

        System.out.println("🔍 Processing " + espnGames.size() + " completed ESPN games for " + sport);
        List<Game> allGames = gameRepository.findAll();
        Set<String> matchedEspnGames = new HashSet<>();

        for (JsonNode espnGame : espnGames) {
            JsonNode competition = espnGame.path("competitions").get(0);
            JsonNode competitors = competition.path("competitors");

            String team1Name = "", team2Name = "";
            int team1Score = 0, team2Score = 0;

            for (JsonNode competitor : competitors) {
                String name = competitor.path("team").path("displayName").asText("");
                int score = competitor.path("score").asInt(0);

                if (team1Name.isEmpty()) {
                    team1Name = name;
                    team1Score = score;
                } else {
                    team2Name = name;
                    team2Score = score;
                }
            }

            for (Game dbGame : allGames) {
                if (dbGame.getSport() != null && !sport.equalsIgnoreCase(dbGame.getSport())) continue;
                if (dbGame.getCommenceTime() != null && dbGame.getCommenceTime().isAfter(java.time.Instant.now())) continue;

                boolean directMatch = isTeamMatch(dbGame.getHomeTeam(), team1Name) && isTeamMatch(dbGame.getAwayTeam(), team2Name);
                boolean swappedMatch = isTeamMatch(dbGame.getHomeTeam(), team2Name) && isTeamMatch(dbGame.getAwayTeam(), team1Name);

                if (directMatch || swappedMatch) {
                    matchedEspnGames.add(team1Name + "_" + team2Name); // Mark as matched

                    System.out.println("🎯 MATCH FOUND IN DB FOR: " + dbGame.getAwayTeam() + " @ " + dbGame.getHomeTeam());

                    if (isTeamMatch(dbGame.getHomeTeam(), team1Name)) {
                        dbGame.setHomeScore(team1Score);
                        dbGame.setAwayScore(team2Score);
                    } else {
                        dbGame.setHomeScore(team2Score);
                        dbGame.setAwayScore(team1Score);
                    }

                    dbGame.setCompleted(true);
                    gameRepository.save(dbGame);
                    System.out.println("✅ Graded via ESPN: " + dbGame.getAwayTeam() + " (" + dbGame.getAwayScore() + ") @ " + dbGame.getHomeTeam() + " (" + dbGame.getHomeScore() + ")");

                    gradePicksForGame(dbGame);
                    break;
                }
            }
        }

        // --- THE UNMATCHED GAMES LOGGER ---
        int unmatchedCount = 0;
        System.out.println("--------------------------------------------------");
        for (JsonNode espnGame : espnGames) {
            JsonNode competition = espnGame.path("competitions").get(0);
            JsonNode competitors = competition.path("competitors");
            String t1 = competitors.get(0).path("team").path("displayName").asText("");
            String t2 = competitors.get(1).path("team").path("displayName").asText("");

            if (!matchedEspnGames.contains(t1 + "_" + t2) && !matchedEspnGames.contains(t2 + "_" + t1)) {
                unmatchedCount++;
                System.out.println("⚠️ Unmatched ESPN Game: " + t1 + " vs " + t2);
            }
        }
        System.out.println("--------------------------------------------------");
        System.out.println("Matched " + (espnGames.size() - unmatchedCount) + " out of " + espnGames.size() + " completed ESPN games.");
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

            if (pick.getPlayer() != null && pick.getWeekNumber() != null && pick.getLeagueId() != null) {
                updatePlayerRecords(pick.getPlayer(), game.getSport(), pick.getWeekNumber(), pick.getLeagueId());
            }
        }
    }

    private void updatePlayerRecords(Player player, String sport, int weekNumber, Long leagueId) {
        List<Pick> allSportPicks = pickRepository.findByLeagueIdAndPlayerIdAndSport(leagueId, player.getId(), sport);
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

        PlayerRecord weeklyRecord = playerRecordRepo.findByLeagueIdAndPlayerIdAndSportAndWeekNumber(leagueId, player.getId(), sport, weekNumber)
                .orElse(new PlayerRecord(player, sport, weekNumber, leagueId));

        weeklyRecord.setWins(weeklyWins);
        weeklyRecord.setLosses(weeklyLosses);
        weeklyRecord.setPushes(weeklyPushes);
        playerRecordRepo.save(weeklyRecord);
    }

    private boolean isTeamMatch(String dbTeam, String apiTeam) {
        if (dbTeam == null || apiTeam == null) return false;
        String normDb = normalizeTeam(dbTeam);
        String normApi = normalizeTeam(apiTeam);
        if (normDb.equals(normApi)) return true;

        // Expanded string matching to handle slight name variations safely
        if (normApi.startsWith(normDb + " ") || normDb.startsWith(normApi + " ")) return true;
        if (normApi.endsWith(" " + normDb) || normDb.endsWith(" " + normApi)) return true;

        return false;
    }

    private String normalizeTeam(String name) {
        if (name == null) return "";

        // 1. Strip apostrophes completely BEFORE regex replaces them with spaces
        String s = name.toLowerCase().trim().replace("'", "");

        // 2. Standardize special characters
        s = s.replaceAll("&", "and")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // 3. Fix SharpAPI abbreviations
        s = s.replaceAll("\\bst\\b", "state");

        // --- COLLEGE FOOTBALL EXPLICIT MAPPINGS ---
        if (s.contains("lsu") || s.contains("louisiana state")) return "lsu";
        if (s.contains("ole miss") || s.contains("mississippi rebels") || s.equals("mississippi")) return "ole miss";
        if (s.contains("uconn") || s.contains("connecticut")) return "uconn";
        if (s.contains("liu") || s.contains("long island")) return "liu";
        if (s.contains("umass") || s.contains("massachusetts")) return "umass";
        if (s.contains("appalachian state") || s.contains("app state")) return "app state";
        if (s.contains("nc state") || s.contains("north carolina state")) return "nc state";
        if (s.contains("ul monroe") || s.equals("ulm") || s.contains("louisiana monroe")) return "ul monroe";
        if (s.contains("fiu") || s.contains("florida international")) return "fiu";
        if (s.contains("fau") || s.contains("florida atlantic")) return "fau";
        if (s.contains("miami fl") || s.contains("miami hurricanes") || s.equals("miami")) return "miami fl";
        if (s.contains("miami oh") || s.contains("miami ohio") || s.contains("miami oh redhawks")) return "miami oh";
        if (s.contains("hawaii") || s.contains("hawai i")) return "hawaii";
        if (s.contains("pitt") || s.contains("pittsburgh")) return "pittsburgh";
        if (s.contains("usc") || s.contains("southern california")) return "usc";
        if (s.contains("western kentucky") || s.equals("wku")) return "western kentucky";
        if (s.contains("middle tennessee") || s.equals("mtsu")) return "middle tennessee";
        // --- FINAL EDGE CASE OVERRIDES ---
        if (s.contains("merrimack")) return "merrimack";
        if (s.contains("sam houston") || s.contains("sam houston state")) return "sam houston";
        if (s.contains("jacksonville state") || s.contains("jacksonville st")) return "jacksonville state";

        if (s.contains("west virginia") || s.equals("wvu") || s.equals("w virginia")) return "west virginia";
        if (s.contains("texas tech") || s.equals("ttu")) return "texas tech";
        if (s.contains("notre dame") || s.equals("nd")) return "notre dame";
        if (s.contains("texas aandm") || s.contains("texas a and m") || s.contains("texas am") || s.equals("tamu")) return "texas am";

        if (s.contains("san jose state") || s.contains("san jos state") || s.equals("sjsu")) return "san jose state";
        if (s.contains("louisiana ragin") || s.contains("louisiana lafayette") || s.equals("louisiana")) return "louisiana lafayette";
        if (s.contains("uab") || s.contains("alabama birmingham")) return "uab";
        if (s.contains("charlotte")) return "charlotte";
        if (s.contains("unlv") || s.contains("nevada las vegas")) return "unlv";
        if (s.contains("southern miss") || s.contains("southern mississippi")) return "southern miss";
        if (s.contains("army")) return "army";
        if (s.contains("georgia tech")) return "georgia tech";
        if (s.contains("utep") || s.contains("texas el paso")) return "utep";

        // --- STRICT NFL MAPPINGS (Exact or strict token checks to prevent college crossfire) ---
        if (s.equals("arizona cardinals") || s.equals("ari cardinals") || s.equals("ari")) return "arizona cardinals";
        if (s.equals("atlanta falcons") || s.equals("atl falcons") || s.equals("atl")) return "atlanta falcons";
        if (s.equals("baltimore ravens") || s.equals("bal ravens") || s.equals("bal")) return "baltimore ravens";
        if (s.equals("buffalo bills") || s.equals("buf bills") || s.equals("buf")) return "buffalo bills";
        if (s.equals("carolina panthers") || s.equals("car panthers") || s.equals("car")) return "carolina panthers";
        if (s.equals("chicago bears") || s.equals("chi bears") || s.equals("chi")) return "chicago bears";
        if (s.equals("cincinnati bengals") || s.equals("cin bengals") || s.equals("cin")) return "cincinnati bengals";
        if (s.equals("cleveland browns") || s.equals("cle browns") || s.equals("cle")) return "cleveland browns";
        if (s.equals("dallas cowboys") || s.equals("dal cowboys") || s.equals("dal")) return "dallas cowboys";
        if (s.equals("denver broncos") || s.equals("den broncos") || s.equals("den")) return "denver broncos";
        if (s.equals("detroit lions") || s.equals("det lions") || s.equals("det")) return "detroit lions";
        if (s.equals("green bay packers") || s.equals("gb packers") || s.equals("gb")) return "green bay packers";
        if (s.equals("houston texans") || s.equals("hou texans") || s.equals("hou")) return "houston texans";
        if (s.equals("indianapolis colts") || s.equals("ind colts") || s.equals("ind")) return "indianapolis colts";
        if (s.equals("jacksonville jaguars") || s.equals("jax jaguars") || s.equals("jax")) return "jacksonville jaguars";
        if (s.equals("kansas city chiefs") || s.equals("kc chiefs") || s.equals("kc")) return "kansas city chiefs";
        if (s.equals("las vegas raiders") || s.equals("lv raiders") || s.equals("lv")) return "las vegas raiders";
        if (s.equals("los angeles chargers") || s.equals("lac chargers") || s.equals("lac")) return "los angeles chargers";
        if (s.equals("los angeles rams") || s.equals("lar rams") || s.equals("lar")) return "los angeles rams";
        if (s.equals("miami dolphins") || s.equals("mia dolphins") || s.equals("mia")) return "miami dolphins";
        if (s.equals("minnesota vikings") || s.equals("min vikings") || s.equals("min")) return "minnesota vikings";
        if (s.equals("new england patriots") || s.equals("ne patriots") || s.equals("ne")) return "new england patriots";
        if (s.equals("new orleans saints") || s.equals("no saints") || s.equals("no")) return "new orleans saints";
        if (s.equals("new york giants") || s.equals("nyg giants") || s.equals("nyg")) return "new york giants";
        if (s.equals("new york jets") || s.equals("nyj jets") || s.equals("nyj")) return "new york jets";
        if (s.equals("philadelphia eagles") || s.equals("phi eagles") || s.equals("phi")) return "philadelphia eagles";
        if (s.equals("pittsburgh steelers") || s.equals("pit steelers") || s.equals("pit")) return "pittsburgh steelers";
        if (s.equals("san francisco 49ers") || s.equals("sf 49ers") || s.equals("niners") || s.equals("sf")) return "san francisco 49ers";
        if (s.equals("seattle seahawks") || s.equals("sea seahawks") || s.equals("sea")) return "seattle seahawks";
        if (s.equals("tampa bay buccaneers") || s.equals("tb buccaneers") || s.equals("bucs") || s.equals("tb")) return "tampa bay buccaneers";
        if (s.equals("tennessee titans") || s.equals("ten titans") || s.equals("ten")) return "tennessee titans";
        if (s.equals("washington commanders") || s.equals("was commanders") || s.equals("was")) return "washington commanders";

        return s;
    }

    public void printTeamNameComparison() {
        System.out.println("==================================================");
        System.out.println("           TEAM NAME MATCHING SCORECARD           ");
        System.out.println("==================================================");

        List<JsonNode> espnGames = fetchEspnScores("NCAAF");
        List<Game> dbGames = gameRepository.findAll().stream()
                .filter(g -> "NCAAF".equalsIgnoreCase(g.getSport()))
                .toList();

        int matchedCount = 0;
        int unmatchedCount = 0;

        for (JsonNode espnGame : espnGames) {
            JsonNode competitors = espnGame.path("competitions").get(0).path("competitors");
            String espnAway = competitors.get(1).path("team").path("displayName").asText("");
            String espnHome = competitors.get(0).path("team").path("displayName").asText("");

            boolean foundInDb = false;
            for (Game dbGame : dbGames) {
                boolean direct = isTeamMatch(dbGame.getHomeTeam(), espnHome) && isTeamMatch(dbGame.getAwayTeam(), espnAway);
                boolean swapped = isTeamMatch(dbGame.getHomeTeam(), espnAway) && isTeamMatch(dbGame.getAwayTeam(), espnHome);
                if (direct || swapped) {
                    foundInDb = true;
                    break;
                }
            }

            if (foundInDb) {
                matchedCount++;
                System.out.println("✅ MATCHED: " + espnAway + " @ " + espnHome);
            } else {
                unmatchedCount++;
                System.out.println("❌ UNMATCHED IN DB: " + espnAway + " @ " + espnHome);
            }
        }

        System.out.println("--------------------------------------------------");
        System.out.println("Total ESPN Games: " + espnGames.size());
        System.out.println("Successfully Matched: " + matchedCount);
        System.out.println("Still Unmatched: " + unmatchedCount);
        System.out.println("==================================================");

        System.out.println("--- ALL GAMES CURRENTLY IN DATABASE ---");
        List<Game> allDbGames = gameRepository.findAll();
        for (Game g : allDbGames) {
            if ("NCAAF".equalsIgnoreCase(g.getSport())) {
                System.out.println("DB Game: " + g.getAwayTeam() + " @ " + g.getHomeTeam());
            }
        }
    }
}