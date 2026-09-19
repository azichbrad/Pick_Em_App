package com.pickem.app.service;

import com.pickem.app.dto.GameOddsDTO;
import com.pickem.app.model.Game;
import com.pickem.app.repository.GameRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OddsSyncTask {

    private final OddsService oddsService;
    private final GameRepository gameRepository; // Assumes you already have this wired to your games table

    public OddsSyncTask(OddsService oddsService, GameRepository gameRepository) {
        this.oddsService = oddsService;
        this.gameRepository = gameRepository;
    }

    // Executes at the top of every hour (e.g., 1:00, 2:00) using a cron expression
    @Scheduled(cron = "0 0 * * * *")
    public void syncOddsToDatabase() {
        System.out.println("Starting hourly background odds sync...");

        syncSport("NFL");
        syncSport("NCAAF");

        System.out.println("Hourly odds sync completed.");
    }

    private void syncSport(String sport) {
        // This utilizes the OddsService we just built to safely fetch the paginated SharpAPI data
        List<GameOddsDTO> liveOdds = oddsService.getOddsForSport(sport);

        for (GameOddsDTO dto : liveOdds) {
            // Fetch existing game to update, or create a new one if it is the first sync of the week
            Game game = gameRepository.findById(dto.id()).orElse(new Game());

            game.setId(dto.id());
            game.setSport(sport);
            game.setHomeTeam(dto.homeTeam());
            game.setAwayTeam(dto.awayTeam());
            game.setCommenceTime(dto.commenceTime());

            // Map the nested Jackson markets directly into your database columns
            dto.bookmakers().stream()
                    .flatMap(b -> b.markets().stream())
                    .forEach(market -> {
                        if ("spread".equalsIgnoreCase(market.key())) {
                            market.outcomes().forEach(outcome -> {
                                // Note: Adjust the setter methods below to match your Game.java entity exactly
                                if (outcome.name().equals(dto.homeTeam())) game.setHomeSpread(outcome.point());
                                if (outcome.name().equals(dto.awayTeam())) game.setAwaySpread(outcome.point());
                            });
                        } else if ("total".equalsIgnoreCase(market.key())) {
                            market.outcomes().forEach(outcome -> {
                                if ("Over".equalsIgnoreCase(outcome.name())) game.setOverTotal(outcome.point());
                                if ("Under".equalsIgnoreCase(outcome.name())) game.setUnderTotal(outcome.point());
                            });
                        }
                    });

            gameRepository.save(game);
        }
    }
}