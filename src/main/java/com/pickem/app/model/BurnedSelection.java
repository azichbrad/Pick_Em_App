package com.pickem.app.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "burned_selections")
public class BurnedSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "player_id")
    private Player player;

    private String sport;
    private Integer weekNumber;
    private String gameId;

    // Tracks exactly what they abandoned (e.g., "spread", "totals", "team_totals")
    private String marketType;

    // Tracks the specific side (e.g., "Clemson", "Over", "Under")
    private String selectionSide;

    private Instant abandonedAt;

    // --- GETTERS AND SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }

    public String getSport() { return sport; }
    public void setSport(String sport) { this.sport = sport; }

    public Integer getWeekNumber() { return weekNumber; }
    public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getMarketType() { return marketType; }
    public void setMarketType(String marketType) { this.marketType = marketType; }

    public String getSelectionSide() { return selectionSide; }
    public void setSelectionSide(String selectionSide) { this.selectionSide = selectionSide; }

    public Instant getAbandonedAt() { return abandonedAt; }
    public void setAbandonedAt(Instant abandonedAt) { this.abandonedAt = abandonedAt; }
}