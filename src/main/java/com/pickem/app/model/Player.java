package com.pickem.app.model;

import jakarta.persistence.*;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Integer wins = 0;
    private Integer losses = 0;
    private Integer pushes = 0;


    @Column(unique = true)
    private String email;

    @Column(name = "is_premium", nullable = false)
    private boolean isPremium = false;

    private boolean admin;

    // --- GETTERS AND SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getWins() { return wins; }
    public void setWins(Integer wins) { this.wins = wins; }

    public Integer getLosses() { return losses; }
    public void setLosses(Integer losses) { this.losses = losses; }

    public Integer getPushes() { return pushes; }
    public void setPushes(Integer pushes) { this.pushes = pushes; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }

    public boolean isPremium() { return isPremium; }
    public void setPremium(boolean premium) { isPremium = premium; }

    /**
     * Determines if the player has access to premium analytics.
     * True if the player bought it themselves, OR if their league manager bought it for the group.
     */
    public boolean hasPremiumAccess(League currentLeague) {
        if (this.isPremium) {
            return true;
        }
        if (currentLeague != null && currentLeague.isPremium()) {
            return true;
        }
        return false;
    }
}