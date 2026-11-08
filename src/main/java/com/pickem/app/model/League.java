package com.pickem.app.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "leagues")
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // The player ID of the person who created the league
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    // The player ID of the league administrator
    @Column(name = "admin_id")
    private Long adminId;

    // A unique code users can type in to join
    @Column(name = "invite_code", unique = true)
    private String inviteCode;

    @Column(name = "enable_group_parlay")
    private Boolean enableGroupParlay = true;

    @Column(name = "picks_per_week")
    private Integer picksPerWeek = 5;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "league", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LeagueMember> members = new ArrayList<>();

    // Constructors
    public League() {}

    public League(String name, Long ownerId, String inviteCode) {
        this.name = name;
        this.ownerId = ownerId;
        this.inviteCode = inviteCode;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public Boolean getEnableGroupParlay() { return enableGroupParlay; }
    public void setEnableGroupParlay(Boolean enableGroupParlay) { this.enableGroupParlay = enableGroupParlay; }

    public Integer getPicksPerWeek() { return picksPerWeek; }
    public void setPicksPerWeek(Integer picksPerWeek) { this.picksPerWeek = picksPerWeek; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<LeagueMember> getMembers() { return members; }
    public void setMembers(List<LeagueMember> members) { this.members = members; }
}