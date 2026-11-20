package com.pickem.app.service.game;

import com.pickem.app.dto.TeamAtsSplitDTO;
import com.pickem.app.model.TeamAtsSplit;
import com.pickem.app.repository.TeamAtsSplitRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TeamAtsService {

    private static final String UPSERT_SQL = """
            INSERT INTO team_ats_splits (
                sport, season, team_name, situation,
                wins, losses, pushes, cover_pct, ats_margin, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (sport, season, team_name, situation)
            DO UPDATE SET
                wins       = EXCLUDED.wins,
                losses     = EXCLUDED.losses,
                pushes     = EXCLUDED.pushes,
                cover_pct  = EXCLUDED.cover_pct,
                ats_margin = EXCLUDED.ats_margin,
                updated_at = NOW()
            """;

    private final TeamAtsSplitRepository atsRepo;
    private final JdbcTemplate jdbcTemplate;

    public TeamAtsService(TeamAtsSplitRepository atsRepo, JdbcTemplate jdbcTemplate) {
        this.atsRepo = atsRepo;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int upsertSplits(String sport, int season, List<TeamAtsSplitDTO> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return 0;
        }

        String normalizedSport = sport.trim().toUpperCase();

        // Deduplicate and sort deterministically so concurrent upserts never deadlock
        Map<String, TeamAtsSplitDTO> deduped = new TreeMap<>();
        for (TeamAtsSplitDTO dto : incoming) {
            if (dto.teamName() == null || dto.teamName().isBlank()
                    || dto.situation() == null || dto.situation().isBlank()) {
                continue;
            }
            String teamName = dto.teamName().trim();
            String situation = dto.situation().trim().toUpperCase();
            String key = teamName.toLowerCase() + "|" + situation;
            deduped.put(key, new TeamAtsSplitDTO(
                    teamName, situation,
                    dto.wins(), dto.losses(), dto.pushes(),
                    dto.coverPct(), dto.atsMargin()
            ));
        }

        List<TeamAtsSplitDTO> batch = new ArrayList<>(deduped.values());

        jdbcTemplate.batchUpdate(UPSERT_SQL, batch, 200, (ps, dto) -> {
            ps.setString(1, normalizedSport);
            ps.setInt(2, season);
            ps.setString(3, dto.teamName());
            ps.setString(4, dto.situation());
            ps.setInt(5, dto.wins());
            ps.setInt(6, dto.losses());
            ps.setInt(7, dto.pushes());
            ps.setDouble(8, dto.coverPct());
            ps.setDouble(9, dto.atsMargin());
        });

        return batch.size();
    }

    public List<TeamAtsSplit> getRankedSplits(String sport, int season, String situation) {
        List<TeamAtsSplit> splits = atsRepo.findBySportAndSeasonAndSituation(
                sport.trim().toUpperCase(),
                season,
                situation.trim().toUpperCase()
        );

        splits.sort(Comparator
                .comparingInt((TeamAtsSplit s) -> (s.getWins() + s.getLosses()) > 0 ? 0 : 1)
                .thenComparing(Comparator.comparingDouble(TeamAtsSplit::getCoverPct).reversed())
                .thenComparing(Comparator.comparingInt(TeamAtsSplit::getNetCovers).reversed())
                .thenComparing(Comparator.comparingDouble(TeamAtsSplit::getAtsMargin).reversed())
                .thenComparing(TeamAtsSplit::getTeamName));

        return splits;
    }

    public Map<String, Map<String, TeamAtsSplit>> getSplitsByTeam(String sport, int season, Collection<String> teamNames) {
        if (teamNames == null || teamNames.isEmpty()) {
            return Collections.emptyMap();
        }

        List<TeamAtsSplit> rows = atsRepo.findBySportAndSeasonAndTeamNameIn(
                sport.trim().toUpperCase(), season, teamNames
        );

        Map<String, Map<String, TeamAtsSplit>> result = new HashMap<>();
        for (TeamAtsSplit row : rows) {
            result.computeIfAbsent(row.getTeamName(), k -> new HashMap<>())
                    .put(row.getSituation(), row);
        }
        return result;
    }
}