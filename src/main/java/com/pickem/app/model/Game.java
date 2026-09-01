package com.pickem.app.model;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "games")
public class Game {

    @Id
    private String id;

    private String sport;
    private String homeTeam;
    private String awayTeam;

    // --- NEW LOGO FIELDS ---
    private String homeLogo;
    private String awayLogo;

    private Instant commenceTime;
    private Boolean completed = false;

    private Double awaySpread;
    private Double homeSpread;
    private Double overTotal;
    private Double underTotal;

    private Integer homeScore;
    private Integer awayScore;

    // --- GETTERS AND SETTERS ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSport() { return sport; }
    public void setSport(String sport) { this.sport = sport; }

    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }

    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }

    // --- NEW LOGO GETTERS AND SETTERS ---
    public String getHomeLogo() { return homeLogo; }
    public void setHomeLogo(String homeLogo) { this.homeLogo = homeLogo; }

    public String getAwayLogo() { return awayLogo; }
    public void setAwayLogo(String awayLogo) { this.awayLogo = awayLogo; }

    public Instant getCommenceTime() { return commenceTime; }
    public void setCommenceTime(Instant commenceTime) { this.commenceTime = commenceTime; }

    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }

    public Double getAwaySpread() { return awaySpread; }
    public void setAwaySpread(Double awaySpread) { this.awaySpread = awaySpread; }

    public Double getHomeSpread() { return homeSpread; }
    public void setHomeSpread(Double homeSpread) { this.homeSpread = homeSpread; }

    public Double getOverTotal() { return overTotal; }
    public void setOverTotal(Double overTotal) { this.overTotal = overTotal; }

    public Double getUnderTotal() { return underTotal; }
    public void setUnderTotal(Double underTotal) { this.underTotal = underTotal; }

    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }

    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }
}