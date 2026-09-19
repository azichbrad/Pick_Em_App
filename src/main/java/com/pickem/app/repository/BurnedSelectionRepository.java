package com.pickem.app.repository;

import com.pickem.app.model.BurnedSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BurnedSelectionRepository extends JpaRepository<BurnedSelection, Long> {
    boolean existsByPlayerIdAndSportAndWeekNumberAndGameIdAndMarketTypeAndSelectionSide(
            Long playerId, String sport, Integer weekNumber, String gameId, String marketType, String selectionSide
    );
}