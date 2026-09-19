package com.pickem.app.repository;

import com.pickem.app.model.GameOdds;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface GameOddsRepository extends JpaRepository<GameOdds, String> {

    // Instantly fetches all games for a specific sport (NFL or NCAAF)
    List<GameOdds> findBySport(String sport);

}