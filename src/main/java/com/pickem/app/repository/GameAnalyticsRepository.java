package com.pickem.app.repository;

import com.pickem.app.model.GameAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface GameAnalyticsRepository extends JpaRepository<GameAnalytics, Long> {

    // Allows you to quickly find analytics by the Game ID string
    Optional<GameAnalytics> findByGameId(String gameId);

}