package com.pickem.app.repository;

import com.pickem.app.model.GameAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GameAnalyticsRepository extends JpaRepository<GameAnalytics, Long> {
    @Query("SELECT ga FROM GameAnalytics ga JOIN FETCH ga.game g WHERE UPPER(g.sport) = UPPER(:sport)")
    List<GameAnalytics> findBySportWithGame(@Param("sport") String sport);

    @Query("SELECT ga FROM GameAnalytics ga JOIN FETCH ga.game g WHERE g.id IN :gameIds")
    List<GameAnalytics> findByGameIdsWithGame(@Param("gameIds") Collection<String> gameIds);
    // Allows you to quickly find analytics by the Game ID string
    Optional<GameAnalytics> findByGameId(String gameId);

}