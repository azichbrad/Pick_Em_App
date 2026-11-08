package com.pickem.app.repository;

import com.pickem.app.model.GroupParlayPick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupParlayPickRepository extends JpaRepository<GroupParlayPick, Long> {

    // Add these three isolated methods:
    Optional<GroupParlayPick> findByLeagueIdAndWeekNumberAndPlayerId(Long leagueId, Integer weekNumber, Long playerId);
    int countByLeagueIdAndWeekNumber(Long leagueId, Integer weekNumber);
    List<GroupParlayPick> findByLeagueIdAndWeekNumber(Long leagueId, Integer weekNumber);
}