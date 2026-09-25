package com.pickem.app.controller;

import com.pickem.app.model.Game;
import com.pickem.app.repository.GameRepository;
import com.pickem.app.service.ScoreAndGradingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ScoreAndGradingService gradingService;
    private final GameRepository gameRepository;

    public AdminController(ScoreAndGradingService gradingService, GameRepository gameRepository) {
        this.gradingService = gradingService;
        this.gameRepository = gameRepository;
    }

    @GetMapping("/sync-scores")
    public String syncScores() {
        gradingService.syncScoresAndGrade();
        return "Background score sync completed!";
    }

    @GetMapping("/force-grade")
    public String forceGrade(@RequestParam String team, @RequestParam String opponent, @RequestParam int awayScore, @RequestParam int homeScore) {
        List<Game> games = gameRepository.findAll();
        for (Game game : games) {
            boolean teamMatch = (game.getAwayTeam() != null && game.getAwayTeam().toLowerCase().contains(team.toLowerCase())) ||
                    (game.getHomeTeam() != null && game.getHomeTeam().toLowerCase().contains(team.toLowerCase()));

            boolean opponentMatch = (game.getAwayTeam() != null && game.getAwayTeam().toLowerCase().contains(opponent.toLowerCase())) ||
                    (game.getHomeTeam() != null && game.getHomeTeam().toLowerCase().contains(opponent.toLowerCase()));

            if (teamMatch && opponentMatch) {

                game.setAwayScore(awayScore);
                game.setHomeScore(homeScore);
                game.setCompleted(true);
                gameRepository.save(game);

                gradingService.gradePicksForGame(game);
                return "Successfully graded: " + game.getAwayTeam() + " (" + awayScore + ") @ " + game.getHomeTeam() + " (" + homeScore + ")";
            }
        }
        return "Could not find a game for " + team + " vs " + opponent;
    }
}