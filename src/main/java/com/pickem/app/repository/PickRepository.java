package com.pickem.app.repository;

import com.pickem.app.model.Pick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PickRepository extends JpaRepository<Pick, Long> {

    // Retained globally for the ScoreAndGradingService (so one game score updates all leagues' picks)
    List<Pick> findByGameIdAndStatus(String gameId, String status);
    List<Pick> findByWeekNumberAndSport(Integer weekNumber, String sport);
    // Updated to require League ID
    Optional<Pick> findByLeagueIdAndPlayerIdAndWeekNumberAndSlotNumber(Long leagueId, Long playerId, Integer weekNumber, Integer slotNumber);
    List<Pick> findByLeagueIdAndWeekNumberAndSport(Long leagueId, Integer weekNumber, String sport);
    Optional<Pick> findByLeagueIdAndPlayerIdAndSportAndWeekNumberAndSlotNumber(Long leagueId, Long playerId, String sport, Integer weekNumber, Integer slotNumber);
    List<Pick> findByLeagueIdAndPlayerIdAndSport(Long leagueId, Long playerId, String sport);
}