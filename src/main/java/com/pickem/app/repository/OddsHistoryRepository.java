package com.pickem.app.repository;

import com.pickem.app.model.OddsHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OddsHistoryRepository extends JpaRepository<OddsHistory, Long> {

    // This custom method uses Spring Data magic to automatically write a SQL query
    // that fetches ONLY the newest row for a specific game based on our timestamp!
    Optional<OddsHistory> findFirstByGameIdOrderByCreatedAtDesc(String gameId);
}