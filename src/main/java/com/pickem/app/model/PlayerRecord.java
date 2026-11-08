package com.pickem.app.model;

import jakarta.persistence.*;

@Entity
@Table(name = "player_record")
public class PlayerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "player_id")
    private Player player;

    @Column(name = "league_id")
    private Long leagueId; // Added to isolate records per league

    private String sport;      // "NCAAF" or "NFL"
    private Integer weekNumber; // 1, 2, 3... or 0 for "Overall Season"

    private int wins = 0;
    private int losses = 0;
    private int pushes = 0;

    // Constructors, Getters, and Setters
    public PlayerRecord() {}

    public PlayerRecord(Player player, String sport, Integer weekNumber, Long leagueId) {
        this.player = player;
        this.sport = sport;
        this.weekNumber = weekNumber;
        this.leagueId = leagueId;
    }

    // Getters and Setters...
    public Long getId() { return id; }
    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }
    public Long getLeagueId() { return leagueId; }
    public void setLeagueId(Long leagueId) { this.leagueId = leagueId; }
    public String getSport() { return sport; }
    public void setSport(String sport) { this.sport = sport; }
    public Integer getWeekNumber() { return weekNumber; }
    public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }
    public int getPushes() { return pushes; }
    public void setPushes(int pushes) { this.pushes = pushes; }
}