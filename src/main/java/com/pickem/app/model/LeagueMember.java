package com.pickem.app.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "league_members")
public class LeagueMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    // Links to your existing Player entity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "joined_at")
    private Instant joinedAt = Instant.now();

    // Constructors
    public LeagueMember() {}

    public LeagueMember(League league, Player player) {
        this.league = league;
        this.player = player;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public League getLeague() { return league; }
    public void setLeague(League league) { this.league = league; }

    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
}