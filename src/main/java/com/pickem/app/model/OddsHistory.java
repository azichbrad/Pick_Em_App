package com.pickem.app.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "odds_history")
public class OddsHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // We store the gameId to link it back to the main game
    @Column(name = "game_id", nullable = false)
    private String gameId;

    @Column(name = "home_spread")
    private Double homeSpread;

    @Column(name = "away_spread")
    private Double awaySpread;

    @Column(name = "total")
    private Double total;

    // Automatically stamps the exact millisecond this row was created
    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    // --- Empty Constructor required by Spring ---
    public OddsHistory() {}

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public Double getHomeSpread() { return homeSpread; }
    public void setHomeSpread(Double homeSpread) { this.homeSpread = homeSpread; }

    public Double getAwaySpread() { return awaySpread; }
    public void setAwaySpread(Double awaySpread) { this.awaySpread = awaySpread; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public Instant getCreatedAt() { return createdAt; }
}