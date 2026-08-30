package com.pickem.app.repository;

import com.pickem.app.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface GameRepository extends JpaRepository<Game, String> {

    // We will use this to grab the cached games for the UI
    List<Game> findBySportOrderByCommenceTimeAsc(String sport);

    // We will use this to find games that need live scores updated
    List<Game> findByCompletedFalseAndCommenceTimeBefore(Instant currentTime);
}