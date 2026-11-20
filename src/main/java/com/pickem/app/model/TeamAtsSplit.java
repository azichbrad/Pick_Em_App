package com.pickem.app.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "team_ats_splits",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_team_ats_split",
                columnNames = {"sport", "season", "team_name", "situation"}
        )
)
public class TeamAtsSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String sport;

    @Column(nullable = false)
    private Integer season;

    @Column(name = "team_name", nullable = false, length = 100)
    private String teamName;

    @Column(nullable = false, length = 40)
    private String situation;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(nullable = false)
    private int pushes;

    @Column(name = "cover_pct", nullable = false)
    private double coverPct;

    @Column(name = "ats_margin", nullable = false)
    private double atsMargin;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public TeamAtsSplit() {}

    public String getFormattedRecord() {
        return wins + "-" + losses + "-" + pushes;
    }

    public int getNetCovers() {
        return wins - losses;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSport() { return sport; }
    public void setSport(String sport) { this.sport = sport; }

    public Integer getSeason() { return season; }
    public void setSeason(Integer season) { this.season = season; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getSituation() { return situation; }
    public void setSituation(String situation) { this.situation = situation; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public int getPushes() { return pushes; }
    public void setPushes(int pushes) { this.pushes = pushes; }

    public double getCoverPct() { return coverPct; }
    public void setCoverPct(double coverPct) { this.coverPct = coverPct; }

    public double getAtsMargin() { return atsMargin; }
    public void setAtsMargin(double atsMargin) { this.atsMargin = atsMargin; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}