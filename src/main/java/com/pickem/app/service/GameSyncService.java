package com.pickem.app.service;

import com.pickem.app.dto.GameOddsDTO;
import com.pickem.app.model.Game;
import com.pickem.app.repository.GameRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GameSyncService {

    private final GameRepository gameRepo;
    private final OddsService oddsService;

    public GameSyncService(GameRepository gameRepo, OddsService oddsService) {
        this.gameRepo = gameRepo;
        this.oddsService = oddsService;
    }

    // Runs every 30 minutes (1,800,000 milliseconds)
    @Scheduled(fixedRate = 1800000)
    @jakarta.annotation.PostConstruct
    public void init() {
        syncAllOdds();
    }
    public void syncAllOdds() {
        System.out.println("Starting background odds sync...");
        syncSport(oddsService.getCollegeFootballOdds(), "NCAAF");
        syncSport(oddsService.getNflOdds(), "NFL");
        System.out.println("Background odds sync completed.");
    }

    private void syncSport(List<GameOddsDTO> apiGames, String sport) {
        if (apiGames == null || apiGames.isEmpty()) return;

        // 1. Extract all the IDs we got from the API
        List<String> apiGameIds = apiGames.stream().map(GameOddsDTO::id).toList();

        // 2. Fetch all existing games from Supabase in ONE single query!
        Map<String, Game> existingGamesMap = gameRepo.findAllById(apiGameIds).stream()
                .collect(Collectors.toMap(Game::getId, g -> g));

        List<Game> gamesToSave = new ArrayList<>();

        for (GameOddsDTO apiGame : apiGames) {
            // Check our local map instead of querying the database
            Game game = existingGamesMap.getOrDefault(apiGame.id(), new Game());

            // THE CLOSING LINE LOCK: If the game has already kicked off, skip it
            if (game.getCommenceTime() != null && game.getCommenceTime().isBefore(Instant.now())) {
                continue;
            }

            // Map standard game details
            game.setId(apiGame.id());
            game.setSport(sport);
            game.setHomeTeam(apiGame.homeTeam());
            game.setAwayTeam(apiGame.awayTeam());
            game.setCommenceTime(apiGame.commenceTime());

            // Extract and map the Spread
            apiGame.bookmakers().stream()
                    .flatMap(b -> b.markets().stream())
                    .filter(m -> "spreads".equalsIgnoreCase(m.key()))
                    .findFirst()
                    .ifPresent(market -> {
                        for (GameOddsDTO.OutcomeDTO outcome : market.outcomes()) {
                            if (outcome.name().equals(apiGame.awayTeam())) {
                                game.setAwaySpread(outcome.point());
                            } else if (outcome.name().equals(apiGame.homeTeam())) {
                                game.setHomeSpread(outcome.point());
                            }
                        }
                    });

            // Extract and map the Totals
            apiGame.bookmakers().stream()
                    .flatMap(b -> b.markets().stream())
                    .filter(m -> "totals".equalsIgnoreCase(m.key()))
                    .findFirst()
                    .ifPresent(market -> {
                        for (GameOddsDTO.OutcomeDTO outcome : market.outcomes()) {
                            if ("Over".equalsIgnoreCase(outcome.name())) {
                                game.setOverTotal(outcome.point());
                            } else if ("Under".equalsIgnoreCase(outcome.name())) {
                                game.setUnderTotal(outcome.point());
                            }
                        }
                    });

            gamesToSave.add(game);
        }

        // 3. Save all updated games in one massive batch command
        gameRepo.saveAll(gamesToSave);
    }
}