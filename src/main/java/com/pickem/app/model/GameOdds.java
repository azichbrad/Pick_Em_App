package com.pickem.app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "game_odds")
public class GameOdds {

    @Id
    private String gameId; // SharpAPI event_id

    private String sport; // "NFL" or "NCAAF"
    private String homeTeam;
    private String awayTeam;
    private Instant commenceTime;

    private Double homeSpreadPoint;
    private Double awaySpreadPoint;
    private Double overTotalPoint;
    private Double underTotalPoint;

    // --- GETTERS AND SETTERS ---

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getSport() { return sport; }
    public void setSport(String sport) { this.sport = sport; }

    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }

    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }

    public Instant getCommenceTime() { return commenceTime; }
    public void setCommenceTime(Instant commenceTime) { this.commenceTime = commenceTime; }

    public Double getHomeSpreadPoint() { return homeSpreadPoint; }
    public void setHomeSpreadPoint(Double homeSpreadPoint) { this.homeSpreadPoint = homeSpreadPoint; }

    public Double getAwaySpreadPoint() { return awaySpreadPoint; }
    public void setAwaySpreadPoint(Double awaySpreadPoint) { this.awaySpreadPoint = awaySpreadPoint; }

    public Double getOverTotalPoint() { return overTotalPoint; }
    public void setOverTotalPoint(Double overTotalPoint) { this.overTotalPoint = overTotalPoint; }

    public Double getUnderTotalPoint() { return underTotalPoint; }
    public void setUnderTotalPoint(Double underTotalPoint) { this.underTotalPoint = underTotalPoint; }
}