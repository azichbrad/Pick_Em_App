package com.pickem.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pickem.app.dto.GameOddsDTO;
import com.pickem.app.model.Game;
import com.pickem.app.model.OddsHistory;
import com.pickem.app.model.Pick;
import com.pickem.app.model.Player;
import com.pickem.app.model.PlayerRecord;
import com.pickem.app.repository.GameRepository;
import com.pickem.app.repository.OddsHistoryRepository;
import com.pickem.app.repository.PickRepository;
import com.pickem.app.repository.PlayerRecordRepository;
import com.pickem.app.repository.PlayerRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GameSyncService {

    private final GameRepository gameRepo;
    private final PlayerRepository playerRepo;
    private final OddsService oddsService;
    private final PickRepository pickRepo;
    private final PlayerRecordRepository playerRecordRepo;
    private final OddsHistoryRepository oddsHistoryRepo;
    private final RestTemplate restTemplate;

    @Value("${cfbd.api.key}")
    private String cfbdApiKey;

    private final Map<String, String> logoCache = new HashMap<>();

    private static final Map<String, String> NFL_ABBREVIATIONS = Map.ofEntries(
            Map.entry("arizona cardinals", "ari"), Map.entry("atlanta falcons", "atl"),
            Map.entry("baltimore ravens", "bal"), Map.entry("buffalo bills", "buf"),
            Map.entry("carolina panthers", "car"), Map.entry("chicago bears", "chi"),
            Map.entry("cincinnati bengals", "cin"), Map.entry("cleveland browns", "cle"),
            Map.entry("dallas cowboys", "dal"), Map.entry("denver broncos", "den"),
            Map.entry("detroit lions", "det"), Map.entry("green bay packers", "gb"),
            Map.entry("houston texans", "hou"), Map.entry("indianapolis colts", "ind"),
            Map.entry("jacksonville jaguars", "jax"), Map.entry("kansas city chiefs", "kc"),
            Map.entry("las vegas raiders", "lv"), Map.entry("los angeles chargers", "lac"),
            Map.entry("los angeles rams", "lar"), Map.entry("miami dolphins", "mia"),
            Map.entry("minnesota vikings", "min"), Map.entry("new england patriots", "ne"),
            Map.entry("new orleans saints", "no"), Map.entry("new york giants", "nyg"),
            Map.entry("new york jets", "nyj"), Map.entry("philadelphia eagles", "phi"),
            Map.entry("pittsburgh steelers", "pit"), Map.entry("san francisco 49ers", "sf"),
            Map.entry("seattle seahawks", "sea"), Map.entry("tampa bay buccaneers", "tb"),
            Map.entry("tennessee titans", "ten"), Map.entry("washington commanders", "was")
    );

    private static final Map<String, String> MANUAL_OVERRIDES = Map.ofEntries(
            Map.entry("hawaii", "hawai'i"),
            Map.entry("hawaii rainbow warriors", "hawai'i"),
            Map.entry("san jose state", "san josé state"),
            Map.entry("san jose state spartans", "san josé state"),
            Map.entry("miami florida", "miami"),
            Map.entry("miami fl", "miami"),
            Map.entry("miami ohio", "miami (oh)"),
            Map.entry("miami (ohio)", "miami (oh)"),
            Map.entry("louisiana monroe", "louisiana-monroe"),
            Map.entry("ul monroe", "louisiana-monroe"),
            Map.entry("louisiana lafayette", "louisiana"),
            Map.entry("louisiana ragin cajuns", "louisiana"),
            Map.entry("appalachian state", "app state"),
            Map.entry("albany", "ualbany"),
            Map.entry("citadel bulldogs", "the citadel"),
            Map.entry("nicholls state colonels", "nicholls"),
            Map.entry("southeastern louisiana lions", "southeastern louisiana")
    );

    private static final Map<String, String> DIRECT_LOGO_FALLBACKS = Map.of(
            "texas southern", "https://a.espncdn.com/i/teamlogos/ncaa/500/2640.png",
            "mcneese", "https://a.espncdn.com/i/teamlogos/ncaa/500/2377.png",
            "tarleton state", "https://a.espncdn.com/i/teamlogos/ncaa/500/2607.png",
            "central arkansas", "https://a.espncdn.com/i/teamlogos/ncaa/500/2110.png",
            "ul monroe", "https://a.espncdn.com/i/teamlogos/ncaa/500/2433.png"
    );

    public GameSyncService(PickRepository pickRepo, GameRepository gameRepo, OddsService oddsService,
                           PlayerRepository playerRepo, PlayerRecordRepository playerRecordRepo,
                           OddsHistoryRepository oddsHistoryRepo, RestTemplateBuilder restTemplateBuilder) {
        this.gameRepo = gameRepo;
        this.oddsService = oddsService;
        this.pickRepo = pickRepo;
        this.playerRepo = playerRepo;
        this.playerRecordRepo = playerRecordRepo;
        this.oddsHistoryRepo = oddsHistoryRepo;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Transactional
    public void simulateAndGradeWeek(int weekNumber, String sport) {
        System.out.println("🚀 Starting simulation for Week " + weekNumber + " (" + sport + ")");

        Instant now = Instant.now();
        List<Game> games = gameRepo.findAll();

        for (Game game : games) {
            // Guardrail: Never simulate or finalize upcoming games
            if (game.getCommenceTime() != null && game.getCommenceTime().isAfter(now)) {
                continue;
            }
            game.setAwayScore((int) (Math.random() * 45));
            game.setHomeScore((int) (Math.random() * 45));
            game.setCompleted(true);
        }
        gameRepo.saveAll(games);

        List<Pick> picks = pickRepo.findByWeekNumberAndSport(weekNumber, sport);

        for (Pick pick : picks) {
            if (pick.getGameId() == null || pick.getId() == null) continue;

            Game game = gameRepo.findById(pick.getGameId()).orElse(null);
            if (game == null || !Boolean.TRUE.equals(game.getCompleted())) continue;

            double lockedPoint = pick.getLockedPoint();
            String status = "PENDING";

            if (pick.getSelection().contains(" O ") || pick.getSelection().contains(" U ")) {
                int totalPoints = game.getAwayScore() + game.getHomeScore();
                boolean isOver = pick.getSelection().contains(" O ");

                if (totalPoints > lockedPoint) status = isOver ? "WIN" : "LOSS";
                else if (totalPoints < lockedPoint) status = isOver ? "LOSS" : "WIN";
                else status = "PUSH";
            } else {
                boolean isAway = pick.getSelection().startsWith(game.getAwayTeam());
                double margin = isAway ? (game.getAwayScore() - game.getHomeScore())
                        : (game.getHomeScore() - game.getAwayScore());

                if (margin + lockedPoint > 0) status = "WIN";
                else if (margin + lockedPoint < 0) status = "LOSS";
                else status = "PUSH";
            }
            pick.setStatus(status);
        }

        List<Pick> validPicksToSave = picks.stream().filter(p -> p.getId() != null).toList();
        pickRepo.saveAll(validPicksToSave);

        Map<Player, List<Pick>> picksByPlayer = validPicksToSave.stream()
                .collect(Collectors.groupingBy(Pick::getPlayer));

        for (Map.Entry<Player, List<Pick>> entry : picksByPlayer.entrySet()) {
            Player player = entry.getKey();
            List<Pick> playerPicks = entry.getValue();

            int weeklyWins = (int) playerPicks.stream().filter(p -> "WIN".equals(p.getStatus())).count();
            int weeklyLosses = (int) playerPicks.stream().filter(p -> "LOSS".equals(p.getStatus())).count();
            int weeklyPushes = (int) playerPicks.stream().filter(p -> "PUSH".equals(p.getStatus())).count();

            PlayerRecord weeklyRecord = playerRecordRepo.findByPlayerIdAndSportAndWeekNumber(player.getId(), sport, weekNumber)
                    .orElse(new PlayerRecord(player, sport, weekNumber));
            weeklyRecord.setWins(weeklyWins);
            weeklyRecord.setLosses(weeklyLosses);
            weeklyRecord.setPushes(weeklyPushes);
            playerRecordRepo.save(weeklyRecord);

            List<Pick> allSportPicks = pickRepo.findByPlayerIdAndSport(player.getId(), sport);
            int overallWins = (int) allSportPicks.stream().filter(p -> "WIN".equals(p.getStatus())).count();
            int overallLosses = (int) allSportPicks.stream().filter(p -> "LOSS".equals(p.getStatus())).count();
            int overallPushes = (int) allSportPicks.stream().filter(p -> "PUSH".equals(p.getStatus())).count();

            PlayerRecord overallRecord = playerRecordRepo.findByPlayerIdAndSportAndWeekNumber(player.getId(), sport, 0)
                    .orElse(new PlayerRecord(player, sport, 0));
            overallRecord.setWins(overallWins);
            overallRecord.setLosses(overallLosses);
            overallRecord.setPushes(overallPushes);
            playerRecordRepo.save(overallRecord);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        syncAllOdds();
    }

    public void syncAllOdds() {
        System.out.println("🚀 Starting background odds sync...");

        if (logoCache.isEmpty()) {
            fetchAndCacheLogos();
        }

        try {
            System.out.println("🏈 Fetching NCAAF odds...");
            syncSport(oddsService.getCollegeFootballOdds(), "NCAAF");
        } catch (Exception e) {
            System.err.println("❌ Error syncing NCAAF odds: " + e.getMessage());
        }

        try {
            System.out.println("🏈 Fetching NFL odds...");
            syncSport(oddsService.getNflOdds(), "NFL");
        } catch (Exception e) {
            System.err.println("❌ Error syncing NFL odds: " + e.getMessage());
        }

        System.out.println("✅ Background odds sync completed.");
    }

    private void fetchAndCacheLogos() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(cfbdApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            String cfbdUrl = "https://api.collegefootballdata.com/teams/fbs?year=2026";

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    cfbdUrl,
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                buildLogoCache(response.getBody());
                System.out.println("✅ Successfully built team logo cache!");
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to fetch CFBD logos: " + e.getMessage());
        }
    }

    private void syncSport(List<GameOddsDTO> apiOddsList, String sport) {
        if (apiOddsList == null || apiOddsList.isEmpty()) {
            System.out.println("⚠️ No odds data received for sport: " + sport + ". Skipping update.");
            return;
        }

        System.out.println("📊 Processing " + apiOddsList.size() + " odds items for " + sport);

        // Group by base ID, discarding any items without event IDs or team names
        Map<String, List<GameOddsDTO>> oddsByGame = apiOddsList.stream()
                .filter(dto -> dto.eventId() != null && dto.homeTeam() != null && dto.awayTeam() != null)
                .collect(Collectors.groupingBy(dto -> getBaseEventId(dto.eventId())));

        List<Game> existingDbGames = gameRepo.findAll().stream()
                .filter(g -> sport.equalsIgnoreCase(g.getSport()))
                .collect(Collectors.toList());

        Map<String, Game> gamesToSaveMap = new HashMap<>();
        List<OddsHistory> historyToSaveList = new ArrayList<>();
        Instant now = Instant.now();

        for (Map.Entry<String, List<GameOddsDTO>> entry : oddsByGame.entrySet()) {
            try {
                String baseEventId = entry.getKey();
                List<GameOddsDTO> gameOdds = entry.getValue();
                if (gameOdds.isEmpty()) continue;

                GameOddsDTO baseInfo = gameOdds.get(0);
                if (baseInfo.homeTeam() == null || baseInfo.awayTeam() == null) continue;

                // 1. FBS Gatekeeper: Validate that logos exist for both teams
                String awayLogo = getLogoUrl(baseInfo.awayTeam(), sport);
                String homeLogo = getLogoUrl(baseInfo.homeTeam(), sport);

                if (awayLogo == null || homeLogo == null) {
                    continue; // Drops FCS programs
                }

                Instant latestKickoff = gameOdds.stream()
                        .map(GameOddsDTO::eventStartTime)
                        .filter(Objects::nonNull)
                        .max(Instant::compareTo)
                        .orElse(baseInfo.eventStartTime());

                String canonicalKey = buildCanonicalMatchupKey(sport, baseInfo.homeTeam(), baseInfo.awayTeam(), latestKickoff);

                Game game = gamesToSaveMap.get(canonicalKey);

                if (game == null) {
                    game = existingDbGames.stream()
                            .filter(g -> isSameMatchup(g, baseEventId, sport, baseInfo.homeTeam(), baseInfo.awayTeam(), latestKickoff))
                            .findFirst()
                            .orElse(null);
                }

                if (game == null) {
                    game = new Game();
                    game.setId(baseEventId);
                }

                if (game.getCommenceTime() != null && game.getCommenceTime().isBefore(now)) {
                    continue;
                }

                Double previousAwaySpread = game.getAwaySpread();
                Double previousHomeSpread = game.getHomeSpread();
                Double previousTotal = game.getOverTotal();

                game.setSport(sport);
                game.setHomeTeam(baseInfo.homeTeam());
                game.setAwayTeam(baseInfo.awayTeam());
                game.setHomeLogo(homeLogo);
                game.setAwayLogo(awayLogo);
                game.setCommenceTime(latestKickoff);

                // Main line extraction: prefer is_main_line == true
                for (GameOddsDTO odd : gameOdds) {
                    if (odd.selection() == null || odd.line() == null) continue;

                    // If SharpAPI provides the is_main_line flag, enforce it!
                    if (Boolean.FALSE.equals(odd.isMainLine())) {
                        continue;
                    }

                    String selection = odd.selection().trim().toLowerCase();
                    String awayTeam = game.getAwayTeam() != null ? game.getAwayTeam().trim().toLowerCase() : "";
                    String homeTeam = game.getHomeTeam() != null ? game.getHomeTeam().trim().toLowerCase() : "";

                    if ("point_spread".equalsIgnoreCase(odd.marketType())) {
                        if (selection.equalsIgnoreCase(awayTeam) || awayTeam.contains(selection) || selection.contains(awayTeam)) {
                            game.setAwaySpread(odd.line());
                            game.setHomeSpread(-odd.line());
                        } else if (selection.equalsIgnoreCase(homeTeam) || homeTeam.contains(selection) || selection.contains(homeTeam)) {
                            game.setHomeSpread(odd.line());
                            game.setAwaySpread(-odd.line());
                        }
                    } else if ("total_points".equalsIgnoreCase(odd.marketType())) {
                        game.setOverTotal(odd.line());
                        game.setUnderTotal(odd.line());
                    }
                }

                gamesToSaveMap.put(canonicalKey, game);

                boolean linesChanged = !Objects.equals(previousAwaySpread, game.getAwaySpread()) ||
                        !Objects.equals(previousHomeSpread, game.getHomeSpread()) ||
                        !Objects.equals(previousTotal, game.getOverTotal());

                if (linesChanged && game.getId() != null) {
                    OddsHistory newOdds = new OddsHistory();
                    newOdds.setGameId(game.getId());
                    newOdds.setHomeSpread(game.getHomeSpread());
                    newOdds.setAwaySpread(game.getAwaySpread());
                    newOdds.setTotal(game.getOverTotal());
                    historyToSaveList.add(newOdds);
                }
            } catch (Exception loopEx) {
                System.err.println("⚠️ Error processing individual game in " + sport + ": " + loopEx.getMessage());
            }
        }

        gameRepo.saveAll(gamesToSaveMap.values());
        oddsHistoryRepo.saveAll(historyToSaveList);
        System.out.println("✅ Saved " + gamesToSaveMap.size() + " unique games and logged " + historyToSaveList.size() + " line movements for " + sport);
    }

    private boolean isSameMatchup(Game existing, String baseEventId, String sport, String homeTeam, String awayTeam, Instant commenceTime) {
        if (!sport.equalsIgnoreCase(existing.getSport())) return false;

        // Direct ID match
        if (existing.getId() != null && getBaseEventId(existing.getId()).equalsIgnoreCase(baseEventId)) {
            return true;
        }

        // Canonical string comparison (strip special characters & lowercase)
        String cleanExHome = normalizeForMatching(existing.getHomeTeam());
        String cleanExAway = normalizeForMatching(existing.getAwayTeam());
        String cleanNewHome = normalizeForMatching(homeTeam);
        String cleanNewAway = normalizeForMatching(awayTeam);

        boolean teamsMatch = (cleanExHome.equals(cleanNewHome) && cleanExAway.equals(cleanNewAway)) ||
                (cleanExHome.equals(cleanNewAway) && cleanExAway.equals(cleanNewHome));

        if (!teamsMatch) return false;

        // If times are present, ensure they are within 48 hours of each other to handle rescheduling/buckets
        if (existing.getCommenceTime() != null && commenceTime != null) {
            long hoursDiff = Math.abs(Duration.between(existing.getCommenceTime(), commenceTime).toHours());
            return hoursDiff <= 48;
        }

        return true;
    }

    private String buildCanonicalMatchupKey(String sport, String homeTeam, String awayTeam, Instant commenceTime) {
        String h = normalizeForMatching(homeTeam);
        String a = normalizeForMatching(awayTeam);
        String date = "";
        if (commenceTime != null) {
            date = commenceTime.atZone(ZoneId.of("America/Los_Angeles")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        // Alphabetical ordering of teams prevents home/away order differences from creating new keys
        String pair = h.compareTo(a) < 0 ? h + "_vs_" + a : a + "_vs_" + h;
        return sport.toLowerCase() + "_" + pair + "_" + date;
    }

    private String normalizeForMatching(String teamName) {
        if (teamName == null) return "";
        return teamName.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public void buildLogoCache(JsonNode cfbdTeamsArray) {
        for (JsonNode team : cfbdTeamsArray) {
            if (team.has("logos") && team.get("logos").size() > 0) {
                String logoUrl = team.get("logos").get(0).asText();
                String schoolName = team.get("school").asText().toLowerCase();

                String mascot = "";
                if (team.has("mascot") && !team.get("mascot").isNull()) {
                    mascot = team.get("mascot").asText().toLowerCase();
                }

                logoCache.put(schoolName, logoUrl);

                if (!mascot.isEmpty()) {
                    logoCache.put(schoolName + " " + mascot, logoUrl);
                }

                if (team.has("alternateNames")) {
                    for (JsonNode altNode : team.get("alternateNames")) {
                        String altName = altNode.asText().toLowerCase();
                        logoCache.put(altName, logoUrl);

                        if (!mascot.isEmpty()) {
                            logoCache.put(altName + " " + mascot, logoUrl);
                        }
                    }
                }
            }
        }
    }

    public String getLogoUrl(String oddsApiTeamName, String sport) {
        if (oddsApiTeamName == null) return null;

        String cleanName = oddsApiTeamName.trim().toLowerCase();

        if ("NFL".equalsIgnoreCase(sport)) {
            if (cleanName.equals("arizona") || cleanName.equals("az cardinals") || cleanName.equals("ari cardinals")) {
                return "https://a.espncdn.com/i/teamlogos/nfl/500/ari.png";
            }
            if (NFL_ABBREVIATIONS.containsKey(cleanName)) {
                return "https://a.espncdn.com/i/teamlogos/nfl/500/" + NFL_ABBREVIATIONS.get(cleanName) + ".png";
            }
        }

        if (DIRECT_LOGO_FALLBACKS.containsKey(cleanName)) {
            return DIRECT_LOGO_FALLBACKS.get(cleanName);
        }

        String mappedName = MANUAL_OVERRIDES.getOrDefault(cleanName, cleanName);
        return logoCache.get(mappedName);
    }

    private String getBaseEventId(String sharpApiId) {
        if (sharpApiId == null) return null;
        int bIndex = sharpApiId.lastIndexOf("_b");
        if (bIndex > 0) {
            return sharpApiId.substring(0, bIndex);
        }
        return sharpApiId;
    }

    public List<Game> getGamesForSportAndWeekFromDb(String sport, int weekNumber) {
        List<Game> allGames = gameRepo.findAll();

        java.time.ZonedDateTime week1Start = "NFL".equalsIgnoreCase(sport)
                ? java.time.ZonedDateTime.of(2026, 9, 8, 0, 0, 0, 0, java.time.ZoneId.of("America/Los_Angeles"))
                : java.time.ZonedDateTime.of(2026, 8, 30, 0, 0, 0, 0, java.time.ZoneId.of("America/Los_Angeles"));

        Instant windowStart = week1Start.plusDays((weekNumber - 1) * 7L).toInstant();
        Instant windowEnd = week1Start.plusDays(weekNumber * 7L).toInstant();

        return allGames.stream()
                .filter(g -> sport.equalsIgnoreCase(g.getSport()))
                .filter(g -> g.getCommenceTime() != null &&
                        !g.getCommenceTime().isBefore(windowStart) &&
                        g.getCommenceTime().isBefore(windowEnd))
                .sorted(Comparator.comparing(Game::getCommenceTime))
                .toList();
    }
}