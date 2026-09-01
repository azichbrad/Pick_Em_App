package com.pickem.app.repository;

import com.pickem.app.model.PlayerRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRecordRepository extends JpaRepository<PlayerRecord, Long> {
    Optional<PlayerRecord> findByPlayerIdAndSportAndWeekNumber(Long playerId, String sport, Integer weekNumber);
    List<PlayerRecord> findBySportAndWeekNumber(String sport, Integer weekNumber);
}