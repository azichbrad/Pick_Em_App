package com.pickem.app.repository;

import com.pickem.app.model.PlayerRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRecordRepository extends JpaRepository<PlayerRecord, Long> {

    // Updated to require League ID
    Optional<PlayerRecord> findByLeagueIdAndPlayerIdAndSportAndWeekNumber(Long leagueId, Long playerId, String sport, Integer weekNumber);
    List<PlayerRecord> findByLeagueIdAndSportAndWeekNumber(Long leagueId, String sport, Integer weekNumber);
    List<PlayerRecord> findByLeagueIdAndSport(Long leagueId, String sport);
    List<PlayerRecord> findByLeagueIdAndPlayerIdAndWeekNumber(Long leagueId, Long playerId, int weekNumber);
    List<PlayerRecord> findByLeagueIdAndPlayerId(Long leagueId, Long playerId);
    List<PlayerRecord> findByLeagueIdAndPlayerIdAndSport(Long leagueId, Long playerId, String sport);
    List<PlayerRecord> findByLeagueIdAndWeekNumber(Long leagueId, Integer weekNumber);
}