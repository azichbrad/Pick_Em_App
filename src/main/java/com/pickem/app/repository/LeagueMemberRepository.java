package com.pickem.app.repository;

import com.pickem.app.model.LeagueMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeagueMemberRepository extends JpaRepository<LeagueMember, Long> {

    // The @EntityGraph tells Hibernate to JOIN the league table immediately
    // so the data is ready when Vaadin tries to build the dashboard cards.
    @EntityGraph(attributePaths = {"league"})
    List<LeagueMember> findByPlayerId(Long playerId);

    @EntityGraph(attributePaths = {"player"})
    List<LeagueMember> findByLeagueId(Long leagueId);

    boolean existsByLeagueIdAndPlayerId(Long leagueId, Long playerId);
}