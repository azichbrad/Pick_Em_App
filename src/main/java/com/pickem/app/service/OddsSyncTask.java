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
public class OddsSyncTask {

    private final OddsService oddsService;
    private final GameRepository gameRepository; // Assumes you already have this wired to your games table

    public OddsSyncTask(OddsService oddsService, GameRepository gameRepository) {
        this.oddsService = oddsService;
        this.gameRepository = gameRepository;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 * * * *")
    public void syncOddsToDatabase() {
        System.out.println("Starting hourly background odds sync...");

        // Fetch the odds first, then pass them into syncSport
        syncSport(oddsService.getOddsForSport("NFL"), "NFL");
        syncSport(oddsService.getOddsForSport("NCAAF"), "NCAAF");

        System.out.println("Hourly odds sync completed.");
    }

    private void syncSport(List<GameOddsDTO> apiGames, String sport) {
        if (apiGames == null || apiGames.isEmpty()) return;

        // 1. Extract all the IDs we got from the API
        List<String> apiGameIds = apiGames.stream().map(GameOddsDTO::id).toList();

        // 2. Fetch all existing games from Supabase in ONE single query!
        Map<String, Game> existingGamesMap = gameRepository.findAllById(apiGameIds).stream()
                .collect(Collectors.toMap(Game::getId, g -> g));

        List<Game> gamesToSave = new ArrayList<>();

        for (GameOddsDTO apiGame : apiGames) {
            Game game = existingGamesMap.getOrDefault(apiGame.id(), new Game());

            // THE CLOSING LINE LOCK: If the game has already kicked off, skip it
            if (game.getCommenceTime() != null && game.getCommenceTime().isBefore(Instant.now())) {
                continue;
            }

            // Map standard game details (FIXED: Saving real team names, not URLs!)
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
        gameRepository.saveAll(gamesToSave);
    }
}