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

    // The Supabase auth_id (UUID) of the person who created the league
    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    // A unique 6-8 character code users can type in to join
    @Column(name = "invite_code", unique = true)
    private String inviteCode;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "league", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LeagueMember> members = new ArrayList<>();

    // Constructors
    public League() {}

    public League(String name, String ownerId, String inviteCode) {
        this.name = name;
        this.ownerId = ownerId;
        this.inviteCode = inviteCode;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<LeagueMember> getMembers() { return members; }
    public void setMembers(List<LeagueMember> members) { this.members = members; }
}