package com.pickem.app.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "game_analytics")
public class GameAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The owning side of the One-to-One relationship
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", referencedColumnName = "id", nullable = false, unique = true)
    private Game game;

    @Column(name = "away_epa")
    private Double awayEpa;

    @Column(name = "home_epa")
    private Double homeEpa;

    @Column(name = "away_success_rate")
    private Double awaySuccessRate;

    @Column(name = "home_success_rate")
    private Double homeSuccessRate;

    @Column(name = "away_havoc")
    private Double awayHavoc;

    @Column(name = "home_havoc")
    private Double homeHavoc;

    @Column(name = "ai_storyline", columnDefinition = "TEXT")
    private String aiStoryline;

    // JSONB bucket for adding new metrics in the future without altering the database schema
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dynamic_metrics", columnDefinition = "jsonb")
    private Map<String, Object> dynamicMetrics = new HashMap<>();

    // Default Constructor
    public GameAnalytics() {}

    // --- GETTERS AND SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Game getGame() { return game; }
    public void setGame(Game game) { this.game = game; }

    public Double getAwayEpa() { return awayEpa; }
    public void setAwayEpa(Double awayEpa) { this.awayEpa = awayEpa; }

    public Double getHomeEpa() { return homeEpa; }
    public void setHomeEpa(Double homeEpa) { this.homeEpa = homeEpa; }

    public Double getAwaySuccessRate() { return awaySuccessRate; }
    public void setAwaySuccessRate(Double awaySuccessRate) { this.awaySuccessRate = awaySuccessRate; }

    public Double getHomeSuccessRate() { return homeSuccessRate; }
    public void setHomeSuccessRate(Double homeSuccessRate) { this.homeSuccessRate = homeSuccessRate; }

    public Double getAwayHavoc() { return awayHavoc; }
    public void setAwayHavoc(Double awayHavoc) { this.awayHavoc = awayHavoc; }

    public Double getHomeHavoc() { return homeHavoc; }
    public void setHomeHavoc(Double homeHavoc) { this.homeHavoc = homeHavoc; }

    public String getAiStoryline() { return aiStoryline; }
    public void setAiStoryline(String aiStoryline) { this.aiStoryline = aiStoryline; }

    public Map<String, Object> getDynamicMetrics() { return dynamicMetrics; }
    public void setDynamicMetrics(Map<String, Object> dynamicMetrics) { this.dynamicMetrics = dynamicMetrics; }
}