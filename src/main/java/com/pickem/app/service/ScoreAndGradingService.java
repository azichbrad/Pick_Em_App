package com.pickem.app.service;

import com.pickem.app.dto.ScoreDTO;
import com.pickem.app.model.Game;
import com.pickem.app.model.Pick;
import com.pickem.app.repository.GameRepository;
import com.pickem.app.repository.PickRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScoreAndGradingService {

    private final OddsService oddsService;
    private final GameRepository gameRepository;
    private final PickRepository pickRepository;

    public ScoreAndGradingService(OddsService oddsService, GameRepository gameRepository, PickRepository pickRepository) {
        this.oddsService = oddsService;
        this.gameRepository = gameRepository;
        this.pickRepository = pickRepository;
    }

    // Runs every 30 minutes to check for final scores and grade picks
    @Scheduled(fixedDelay = 1800000)
    public void syncScoresAndGrade() {
        System.out.println("Starting score sync and grading engine...");

        List<ScoreDTO> ncaafScores = oddsService.getCompletedScores("americanfootball_ncaaf");
        List<ScoreDTO> nflScores = oddsService.getCompletedScores("americanfootball_nfl");

        processScores(ncaafScores);
        processScores(nflScores);

        System.out.println("Score sync and grading completed.");
    }

    private void processScores(List<ScoreDTO> apiScores) {
        if (apiScores == null || apiScores.isEmpty()) return;

        List<Game> pendingGames = gameRepository.findByCompletedFalse();

        for (ScoreDTO apiScore : apiScores) {
            // Only process games that are fully completed and possess a dual-score array
            if (Boolean.TRUE.equals(apiScore.completed()) && apiScore.scores() != null && apiScore.scores().size() == 2) {

                for (Game dbGame : pendingGames) {
                    // Match by partial name since SharpAPI strings differ slightly from OddsAPI strings
                    if (isTeamMatch(dbGame.getHomeTeam(), apiScore.homeTeam()) &&
                            isTeamMatch(dbGame.getAwayTeam(), apiScore.awayTeam())) {

                        Integer homeScore = null;
                        Integer awayScore = null;

                        for (ScoreDTO.TeamScoreDTO ts : apiScore.scores()) {
                            try {
                                if (ts.name().equals(apiScore.homeTeam())) {
                                    homeScore = Integer.parseInt(ts.score());
                                } else if (ts.name().equals(apiScore.awayTeam())) {
                                    awayScore = Integer.parseInt(ts.score());
                                }
                            } catch (NumberFormatException ignored) {}
                        }

                        if (homeScore != null && awayScore != null) {
                            dbGame.setHomeScore(homeScore);
                            dbGame.setAwayScore(awayScore);
                            dbGame.setCompleted(true);
                            gameRepository.save(dbGame);

                            // Trigger the grading math!
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

                if (pick.getSelectionSide().equals(game.getHomeTeam())) {
                    double adjustedHome = game.getHomeScore() + spread;
                    if (adjustedHome > game.getAwayScore()) newStatus = "WON";
                    else if (adjustedHome < game.getAwayScore()) newStatus = "LOST";
                    else newStatus = "PUSH";
                } else {
                    double adjustedAway = game.getAwayScore() + spread;
                    if (adjustedAway > game.getHomeScore()) newStatus = "WON";
                    else if (adjustedAway < game.getHomeScore()) newStatus = "LOST";
                    else newStatus = "PUSH";
                }
            }
            else if ("total".equalsIgnoreCase(pick.getMarketType())) {
                double totalScore = game.getHomeScore() + game.getAwayScore();
                double lockedTotal = pick.getLockedPoint();

                if ("Over".equalsIgnoreCase(pick.getSelectionSide())) {
                    if (totalScore > lockedTotal) newStatus = "WON";
                    else if (totalScore < lockedTotal) newStatus = "LOST";
                    else newStatus = "PUSH";
                } else if ("Under".equalsIgnoreCase(pick.getSelectionSide())) {
                    if (totalScore < lockedTotal) newStatus = "WON";
                    else if (totalScore > lockedTotal) newStatus = "LOST";
                    else newStatus = "PUSH";
                }
            }

            pick.setStatus(newStatus);
            pickRepository.save(pick);

            // TODO: We can trigger the PlayerRecord / Standings updates right here next!
        }
    }

    private boolean isTeamMatch(String dbTeam, String apiTeam) {
        if (dbTeam == null || apiTeam == null) return false;
        return dbTeam.toLowerCase().contains(apiTeam.toLowerCase()) ||
                apiTeam.toLowerCase().contains(dbTeam.toLowerCase());
    }
}