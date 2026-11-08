package com.pickem.app.service;

import com.pickem.app.model.BurnedSelection;
import com.pickem.app.model.Pick;
import com.pickem.app.model.Player;
import com.pickem.app.repository.BurnedSelectionRepository;
import com.pickem.app.repository.GameRepository;
import com.pickem.app.repository.PickRepository;
import com.vaadin.flow.component.notification.Notification;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class PickService {

    private final PickRepository pickRepo;
    private final BurnedSelectionRepository burnedSelectionRepo;
    private final GameRepository gameRepo;

    public PickService(PickRepository pickRepo, BurnedSelectionRepository burnedSelectionRepo, GameRepository gameRepo) {
        this.pickRepo = pickRepo;
        this.burnedSelectionRepo = burnedSelectionRepo;
        this.gameRepo = gameRepo;
    }

    public boolean isSelectionBurned(Long playerId, String sport, int weekNumber, String gameId, String marketType, String selectionSide) {
        return burnedSelectionRepo.existsByPlayerIdAndSportAndWeekNumberAndGameIdAndMarketTypeAndSelectionSide(
                playerId, sport, weekNumber, gameId, marketType, selectionSide
        );
    }

    @Transactional
    public void savePickWithBurnCheck(Player player, Long leagueId, int slotNumber, String sport, int weekNumber,
                                      String selection, String logoUrl, String gameId, Double lockedPoint,
                                      String matchName, String marketType, String selectionSide, Runnable onPickSaved) {

        com.pickem.app.model.Game game = gameRepo.findById(gameId).orElse(null);
        if (game != null && game.getCommenceTime() != null) {
            java.time.Instant liveCutoff = java.time.Instant.now().minus(java.time.Duration.ofMinutes(15));
            if (game.getCommenceTime().isBefore(liveCutoff)) {
                Notification.show("Too late! This game has already locked.", 4000, Notification.Position.MIDDLE);
                return;
            }
        }

        // Updated to use League ID
        Optional<Pick> existingPickOpt = pickRepo.findByLeagueIdAndPlayerIdAndSportAndWeekNumberAndSlotNumber(
                leagueId, player.getId(), sport, weekNumber, slotNumber
        );

        if (existingPickOpt.isPresent()) {
            Pick existingPick = existingPickOpt.get();

            if (existingPick.getGameId() != null) {
                BurnedSelection burned = new BurnedSelection();
                burned.setPlayer(player);
                burned.setSport(sport);
                burned.setWeekNumber(weekNumber);
                burned.setGameId(existingPick.getGameId());
                burned.setMarketType(existingPick.getMarketType());
                burned.setSelectionSide(existingPick.getSelectionSide());
                burned.setAbandonedAt(Instant.now());
                burnedSelectionRepo.save(burned);
            }

            updatePickData(existingPick, selection, logoUrl, gameId, lockedPoint, matchName, marketType, selectionSide);
            pickRepo.save(existingPick);
        } else {
            Pick newPick = new Pick();
            newPick.setPlayer(player);
            newPick.setLeagueId(leagueId); // Stamping the isolated league
            newPick.setSlotNumber(slotNumber);
            newPick.setSport(sport);
            newPick.setWeekNumber(weekNumber);
            updatePickData(newPick, selection, logoUrl, gameId, lockedPoint, matchName, marketType, selectionSide);
            newPick.setStatus("PENDING");
            pickRepo.save(newPick);
        }

        onPickSaved.run();
    }

    private void updatePickData(Pick pick, String selection, String logoUrl, String gameId, Double lockedPoint, String matchName, String marketType, String selectionSide) {
        pick.setSelection(selection);
        pick.setLogoUrl(logoUrl);
        pick.setGameId(gameId);
        pick.setLockedPoint(lockedPoint);
        pick.setMatchName(matchName);
        pick.setMarketType(marketType);
        pick.setSelectionSide(selectionSide);
    }

    public java.util.Set<String> getBurnedSelectionKeys(Long playerId, String sport, int weekNumber) {
        return burnedSelectionRepo.findByPlayerIdAndSportAndWeekNumber(playerId, sport, weekNumber)
                .stream()
                .map(b -> b.getGameId() + "_" + b.getMarketType() + "_" + b.getSelectionSide())
                .collect(java.util.stream.Collectors.toSet());
    }
}