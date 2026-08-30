package com.pickem.app.service;

import com.pickem.app.dto.ScoreDTO;
import com.pickem.app.model.Pick;
import com.pickem.app.model.Player;
import com.pickem.app.repository.PickRepository;
import com.pickem.app.repository.PlayerRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GradingService {

    private final PickRepository pickRepo;
    private final PlayerRepository playerRepo;
    private final OddsService oddsService;

    public GradingService(PickRepository pickRepo, PlayerRepository playerRepo, OddsService oddsService) {
        this.pickRepo = pickRepo;
        this.playerRepo = playerRepo;
        this.oddsService = oddsService;
    }

    public void gradePendingPicks() {
        List<Pick> pendingPicks = pickRepo.findAll().stream()
                .filter(p -> "PENDING".equals(p.getStatus()))
                .toList();

        if (pendingPicks.isEmpty()) return;

        // Fetch scores for both sports
        List<ScoreDTO> allScoresList = Stream.concat(
                oddsService.getCompletedScores("americanfootball_ncaaf").stream(),
                oddsService.getCompletedScores("americanfootball_nfl").stream()
        ).toList();

        // Map them by Game ID for instant lookup
        Map<String, ScoreDTO> allScores = new HashMap<>();
        for (ScoreDTO score : allScoresList) {
            allScores.put(score.id(), score);
        }

        for (Pick pick : pendingPicks) {
            ScoreDTO game = allScores.get(pick.getGameId());

            if (game == null || game.completed() == null || !game.completed() || game.scores() == null) {
                continue; // Game isn't finished yet
            }

            // Extract scores safely
            Map<String, Integer> scoreMap = game.scores().stream()
                    .filter(s -> s.score() != null)
                    .collect(Collectors.toMap(ScoreDTO.TeamScoreDTO::name, s -> Integer.parseInt(s.score())));

            int homeScore = scoreMap.getOrDefault(game.homeTeam(), 0);
            int awayScore = scoreMap.getOrDefault(game.awayTeam(), 0);

            String selection = pick.getSelection();
            Double lockedPoint = pick.getLockedPoint();
            String status = "PENDING";

            // Grade Totals
            if (selection.contains(" O ") || selection.contains(" U ")) {
                int total = homeScore + awayScore;
                if (selection.contains(" O ")) {
                    status = total > lockedPoint ? "WIN" : (total < lockedPoint ? "LOSS" : "PUSH");
                } else {
                    status = total < lockedPoint ? "WIN" : (total > lockedPoint ? "LOSS" : "PUSH");
                }
            }
            // Grade Spreads
            else {
                if (selection.startsWith(game.awayTeam())) {
                    double adjScore = awayScore + lockedPoint;
                    status = adjScore > homeScore ? "WIN" : (adjScore < homeScore ? "LOSS" : "PUSH");
                } else {
                    double adjScore = homeScore + lockedPoint;
                    status = adjScore > awayScore ? "WIN" : (adjScore < awayScore ? "LOSS" : "PUSH");
                }
            }

            pick.setStatus(status);
            pickRepo.save(pick);
        }

        recalculateStandings();
    }

    private void recalculateStandings() {
        List<Player> players = playerRepo.findAll();
        for (Player player : players) {
            List<Pick> picks = pickRepo.findAll().stream()
                    .filter(p -> p.getPlayer().getId().equals(player.getId()))
                    .toList();

            int wins = (int) picks.stream().filter(p -> "WIN".equals(p.getStatus())).count();
            int losses = (int) picks.stream().filter(p -> "LOSS".equals(p.getStatus())).count();
            int pushes = (int) picks.stream().filter(p -> "PUSH".equals(p.getStatus())).count();

            player.setWins(wins);
            player.setLosses(losses);
            player.setPushes(pushes);
            playerRepo.save(player);
        }
    }


    @Scheduled(cron = "0 0 4 * * *", zone = "America/Los_Angeles")
    public void scheduledDailyGrading() {
        System.out.println("Starting automated daily pick grading...");

        try {
            gradePendingPicks();
            System.out.println("Automated grading completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during automated grading: " + e.getMessage());
        }
    }
}