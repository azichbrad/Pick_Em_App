package com.pickem.app.repository;

import com.pickem.app.model.TeamAtsSplit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TeamAtsSplitRepository extends JpaRepository<TeamAtsSplit, Long> {

    List<TeamAtsSplit> findBySportAndSeason(String sport, Integer season);

    List<TeamAtsSplit> findBySportAndSeasonAndSituation(String sport, Integer season, String situation);

    List<TeamAtsSplit> findBySportAndSeasonAndTeamNameIn(String sport, Integer season, Collection<String> teamNames);
}