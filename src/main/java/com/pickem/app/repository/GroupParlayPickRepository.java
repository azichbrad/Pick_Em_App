package com.pickem.app.repository;

import com.pickem.app.model.GroupParlayPick;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GroupParlayPickRepository extends JpaRepository<GroupParlayPick, Long> {
    List<GroupParlayPick> findByWeekNumber(int weekNumber);
    int countByWeekNumber(int weekNumber);

    // Add this query so each player only has 1 leg per week
    Optional<GroupParlayPick> findByWeekNumberAndPlayerId(int weekNumber, Long playerId);
}