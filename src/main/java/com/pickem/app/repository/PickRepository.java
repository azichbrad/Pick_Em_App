package com.pickem.app.repository;

import com.pickem.app.model.Pick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PickRepository extends JpaRepository<Pick, Long> {

    Optional<Pick> findByPlayerIdAndWeekNumberAndSlotNumber(Long playerId, Integer weekNumber, Integer slotNumber);

    // ADD THIS NEW LINE: Grabs all picks for the board in one shot
    List<Pick> findByWeekNumberAndSport(Integer weekNumber, String sport);
}