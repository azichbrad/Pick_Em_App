package com.pickem.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pickem.app.dto.GameOddsDTO;
import com.pickem.app.model.Game;
import com.pickem.app.model.Pick;
import com.pickem.app.model.Player;
import com.pickem.app.model.PlayerRecord;
import com.pickem.app.repository.GameRepository;
import com.pickem.app.repository.PickRepository;
import com.pickem.app.repository.PlayerRecordRepository;
import com.pickem.app.repository.PlayerRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GameSyncService {

    private final GameRepository gameRepo;
    private final PlayerRepository playerRepo;
    private final OddsService oddsService;
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
    private static final Map<String, String> MANUAL_OVERRIDES = Map.of(
            "albany", "ualbany",
            "san jose state spartans", "san josé state",
            "citadel bulldogs", "the citadel",
            "nicholls state colonels", "nicholls",
            "southeastern louisiana lions", "southeastern louisiana",
            "louisiana ragin cajuns", "louisiana",
            "hawaii rainbow warriors", "hawai'i"
    );
    private final PickRepository pickRepo;
    private final PlayerRecordRepository playerRecordRepo;

    @Value("${cfbd.api.key}")
    private String cfbdApiKey;

    public GameSyncService(PickRepository pickRepo, GameRepository gameRepo, OddsService oddsService, PlayerRepository playerRepo, PlayerRecordRepository playerRecordRepo) {
        this.gameRepo = gameRepo;
        this.oddsService = oddsService;
        this.pickRepo = pickRepo;
        this.playerRepo = playerRepo;
        this.playerRecordRepo = playerRecordRepo;
    }

    @Scheduled(fixedRate = 1800000)
    public void init() {
    }

    @Transactional
    public void simulateAndGradeWeek(int weekNumber, String sport) {
        System.out.println("🚀 Starting simulation for Week " + weekNumber + " (" + sport + ")");

        List<Game> games = gameRepo.findAll();
        System.out.println("🏈 Found " + games.size() + " total games in database.");

        for (Game game : games) {
            game.setAwayScore((int) (Math.random() * 45));
            game.setHomeScore((int) (Math.random() * 45));
            game.setCompleted(true);
        }
        gameRepo.saveAll(games);

        List<Pick> picks = pickRepo.findByWeekNumberAndSport(weekNumber, sport);
        System.out.println("🎯 Found " + picks.size() + " picks matching Week " + weekNumber + " and Sport " + sport);

        for (Pick pick : picks) {
            // Guard: skip if the pick doesn't have an associated gameId or ID
            if (pick.getGameId() == null || pick.getId() == null) {
                continue;
            }

            Game game = gameRepo.findById(pick.getGameId()).orElse(null);
            if (game == null || !Boolean.TRUE.equals(game.getCompleted())) {
                continue;
            }

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

        // 1. Save the graded picks
        List<Pick> validPicksToSave = picks.stream()
                .filter(p -> p.getId() != null)
                .toList();
        pickRepo.saveAll(validPicksToSave);

        // 2. CALCULATE RECORDS PER SPORT (Weekly & Overall)
        Map<Player, List<Pick>> picksByPlayer = validPicksToSave.stream()
                .collect(Collectors.groupingBy(Pick::getPlayer));

        for (Map.Entry<Player, List<Pick>> entry : picksByPlayer.entrySet()) {
            Player player = entry.getKey();
            List<Pick> playerPicks = entry.getValue();

            int weeklyWins = (int) playerPicks.stream().filter(p -> "WIN".equals(p.getStatus())).count();
            int weeklyLosses = (int) playerPicks.stream().filter(p -> "LOSS".equals(p.getStatus())).count();
            int weeklyPushes = (int) playerPicks.stream().filter(p -> "PUSH".equals(p.getStatus())).count();

            // Save or update Weekly Record
            PlayerRecord weeklyRecord = playerRecordRepo.findByPlayerIdAndSportAndWeekNumber(player.getId(), sport, weekNumber)
                    .orElse(new PlayerRecord(player, sport, weekNumber));
            weeklyRecord.setWins(weeklyWins);
            weeklyRecord.setLosses(weeklyLosses);
            weeklyRecord.setPushes(weeklyPushes);
            playerRecordRepo.save(weeklyRecord);

            // Calculate & Save Overall Season Record (Week 0 represents Overall for that Sport)
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

    public void syncAllOdds() {
        System.out.println("Starting background odds sync...");

        // 1. Check if cache is empty. If it is, fetch from CFBD!
        if (logoCache.isEmpty()) {
            fetchAndCacheLogos();
        }

        syncSport(oddsService.getCollegeFootballOdds(), "NCAAF");
        syncSport(oddsService.getNflOdds(), "NFL");
        System.out.println("Background odds sync completed.");
    }

    private void fetchAndCacheLogos() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();

            // This safely formats the "Bearer " string for you
            headers.setBearerAuth(cfbdApiKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String cfbdUrl = "https://api.collegefootballdata.com/teams";

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
        if (apiOddsList == null || apiOddsList.isEmpty()) return;

        // 1. Group the flat SharpAPI rows by event_id so all odds for one game are bundled together
        Map<String, List<GameOddsDTO>> oddsByGame = apiOddsList.stream()
                .filter(dto -> dto.eventId() != null)
                .collect(Collectors.groupingBy(GameOddsDTO::eventId));

        List<String> eventIds = new ArrayList<>(oddsByGame.keySet());
        Map<String, Game> existingGamesMap = gameRepo.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Game::getId, g -> g));

        Map<String, Game> gamesToSaveMap = new HashMap<>();

        for (Map.Entry<String, List<GameOddsDTO>> entry : oddsByGame.entrySet()) {
            String eventId = entry.getKey();
            List<GameOddsDTO> gameOdds = entry.getValue();

            // Grab the first row to get the base game info (teams, time)
            GameOddsDTO baseInfo = gameOdds.get(0);

            Game game = existingGamesMap.getOrDefault(eventId,
                    gamesToSaveMap.getOrDefault(eventId, new Game()));

            if (game.getCommenceTime() != null && game.getCommenceTime().isBefore(Instant.now())) {
                continue;
            }

            game.setId(eventId);
            game.setSport(sport);
            game.setHomeTeam(baseInfo.homeTeam());
            game.setAwayTeam(baseInfo.awayTeam());
            game.setHomeLogo(getLogoUrl(baseInfo.homeTeam()));
            game.setAwayLogo(getLogoUrl(baseInfo.awayTeam()));
            game.setCommenceTime(baseInfo.eventStartTime());

            // 2. Loop through the grouped rows to extract the actual point spreads and totals
            // 2. Loop through the grouped rows to extract the actual point spreads and totals
            for (GameOddsDTO odd : gameOdds) {

                // Match SharpAPI's exact market name: "point_spread"
                if ("point_spread".equalsIgnoreCase(odd.marketType()) && odd.line() != null) {

                    if (odd.selection().equals(game.getAwayTeam())) {
                        game.setAwaySpread(odd.line());
                    } else if (odd.selection().equals(game.getHomeTeam())) {
                        game.setHomeSpread(odd.line());
                    }
                }

                // Match SharpAPI's exact market name: "total_points"
                else if ("total_points".equalsIgnoreCase(odd.marketType()) && odd.line() != null) {

                    if ("Over".equalsIgnoreCase(odd.selection())) {
                        game.setOverTotal(odd.line());
                    } else if ("Under".equalsIgnoreCase(odd.selection())) {
                        game.setUnderTotal(odd.line());
                    }
                }
            }

            gamesToSaveMap.put(game.getId(), game);
        }

        gameRepo.saveAll(gamesToSaveMap.values());
    }

    public void buildLogoCache(JsonNode cfbdTeamsArray) {
        for (JsonNode team : cfbdTeamsArray) {
            // Ensure the team actually has a logo in the array
            if (team.has("logos") && team.get("logos").size() > 0) {
                String logoUrl = team.get("logos").get(0).asText();
                String schoolName = team.get("school").asText().toLowerCase();

                // Safely get the mascot if it exists
                String mascot = "";
                if (team.has("mascot") && !team.get("mascot").isNull()) {
                    mascot = team.get("mascot").asText().toLowerCase();
                }

                // 1. Map the official school name ("massachusetts")
                logoCache.put(schoolName, logoUrl);

                // 2. Map School + Mascot ("massachusetts minutemen")
                if (!mascot.isEmpty()) {
                    logoCache.put(schoolName + " " + mascot, logoUrl);
                }

                // 3. Map Alternate Names & Alternate + Mascot ("umass", "umass minutemen")
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

    public String getLogoUrl(String oddsApiTeamName) {
        if (oddsApiTeamName == null) return null;

        String cleanName = oddsApiTeamName.trim().toLowerCase();

        // 1. Check NFL First
        if (NFL_ABBREVIATIONS.containsKey(cleanName)) {
            return "https://a.espncdn.com/i/teamlogos/nfl/500/" + NFL_ABBREVIATIONS.get(cleanName) + ".png";
        }

        // 2. Check College Overrides
        cleanName = MANUAL_OVERRIDES.getOrDefault(cleanName, cleanName);

        // 3. Check College Cache
        String url = logoCache.get(cleanName);

        if (url == null) {
            System.out.println("❌ CACHE MISS: Odds API handed us -> '" + oddsApiTeamName + "'");
        }

        return url;
    }

    private String getNflLogo(String teamName) {
        Map<String, String> nflLogos = new HashMap<>();

        // Removed Markdown formatting from all ESPN URLs
        nflLogos.put("Pittsburgh Steelers", "https://a.espncdn.com/i/teamlogos/nfl/500/pit.png");
        nflLogos.put("Kansas City Chiefs", "https://a.espncdn.com/i/teamlogos/nfl/500/kc.png");
        nflLogos.put("San Francisco 49ers", "https://a.espncdn.com/i/teamlogos/nfl/500/sf.png");
        nflLogos.put("Baltimore Ravens", "https://a.espncdn.com/i/teamlogos/nfl/500/bal.png");
        nflLogos.put("Los Angeles Rams", "https://a.espncdn.com/i/teamlogos/nfl/500/lar.png");
        nflLogos.put("Los Angeles Chargers", "https://a.espncdn.com/i/teamlogos/nfl/500/lac.png");

        return nflLogos.getOrDefault(teamName, "https://cdn-icons-png.flaticon.com/512/1199/1199155.png");
    }

    public java.util.List<com.pickem.app.model.Game> getGamesForSportAndWeekFromDb(String sport, int weekNumber) {
        java.util.List<com.pickem.app.model.Game> allGames = gameRepo.findAll();

        // NFL Starts Tuesday (Sept 8) -> Ends Monday
        // NCAAF Starts Sunday (Aug 30) -> Ends Saturday
        java.time.ZonedDateTime week1Start = "NFL".equalsIgnoreCase(sport)
                ? java.time.ZonedDateTime.of(2026, 9, 8, 0, 0, 0, 0, java.time.ZoneId.of("America/Los_Angeles"))
                : java.time.ZonedDateTime.of(2026, 8, 30, 0, 0, 0, 0, java.time.ZoneId.of("America/Los_Angeles"));

        java.time.Instant windowStart = week1Start.plusDays((weekNumber - 1) * 7L).toInstant();
        java.time.Instant windowEnd = week1Start.plusDays(weekNumber * 7L).toInstant();

        return allGames.stream()
                .filter(g -> sport.equalsIgnoreCase(g.getSport()))
                .filter(g -> g.getCommenceTime() != null &&
                        !g.getCommenceTime().isBefore(windowStart) &&
                        g.getCommenceTime().isBefore(windowEnd))
                .toList();
    }
}