package com.fittrack.domain;


import java.time.Instant;

public final class User extends BaseEntity {

    private String username;
    private String passwordHash;
    private String salt;
    private Instant createdAt;

    public User() {
    }

    public User(Long id, String username, String passwordHash, String salt, Instant createdAt) {
        super(id);
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.createdAt = createdAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
