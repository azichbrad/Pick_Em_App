package com.pickem.app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class GroupParlayPick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int weekNumber;

    @ManyToOne
    private Player player;

    private String sport;
    private String gameId;
    private String selection;
    private Double lockedPoint;
    private String matchName;
    private String logoUrl;

    // Standard Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getWeekNumber() { return weekNumber; }
    public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }
    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }
    public String getSport() { return sport; }
    public void setSport(String sport) { this.sport = sport; }
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public String getSelection() { return selection; }
    public void setSelection(String selection) { this.selection = selection; }
    public Double getLockedPoint() { return lockedPoint; }
    public void setLockedPoint(Double lockedPoint) { this.lockedPoint = lockedPoint; }
    public String getMatchName() { return matchName; }
    public void setMatchName(String matchName) { this.matchName = matchName; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
}