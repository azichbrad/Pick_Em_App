package com.pickem.app.model;

import jakarta.persistence.*;

@Entity
@Table(name = "picks")
public class Pick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "player_id")
    private Player player;

    private Integer weekNumber; // e.g., Week 0, Week 1
    private Integer slotNumber; // 1 through 5

    private String sport; // "NCAAF" or "NFL"
    private String selection; // e.g., "LSU -7" or "TCU/UNC U46.5"
    private String status; // "PENDING", "WIN", "LOSS", "PUSH"
    private String logoUrl;
    private String gameId;      // Locks the API's unique game ID to fetch final scores later
    private Double lockedPoint; // Locks the exact spread/total number (e.g., 24.5)
    private String matchName;   // Saves the matchup (e.g., "Boise State @ Oregon")

    // --- GETTERS AND SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }

    public Integer getWeekNumber() { return weekNumber; }
    public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

    public Integer getSlotNumber() { return slotNumber; }
    public void setSlotNumber(Integer slotNumber) { this.slotNumber = slotNumber; }

    public String getSport() { return sport; }
    public void setSport(String sport) { this.sport = sport; }

    public String getSelection() { return selection; }
    public void setSelection(String selection) { this.selection = selection; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public Double getLockedPoint() { return lockedPoint; }
    public void setLockedPoint(Double lockedPoint) { this.lockedPoint = lockedPoint; }

    public String getMatchName() { return matchName; }
    public void setMatchName(String matchName) { this.matchName = matchName; }
}