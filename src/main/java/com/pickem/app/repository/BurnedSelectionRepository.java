package com.pickem.app.repository;

import com.pickem.app.model.BurnedSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BurnedSelectionRepository extends JpaRepository<BurnedSelection, Long> {

    List<BurnedSelection> findByPlayerIdAndSportAndWeekNumber(Long playerId, String sport, Integer weekNumber);

    boolean existsByPlayerIdAndSportAndWeekNumberAndGameIdAndMarketTypeAndSelectionSide(
            Long playerId, String sport, Integer weekNumber, String gameId, String marketType, String selectionSide
    );
}