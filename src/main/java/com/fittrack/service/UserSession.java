package com.fittrack.service;

import com.fittrack.domain.user.User;

/**
 * Holds the currently authenticated user for this app process.
 */
public final class UserSession {

    private User currentUser;

    public synchronized void login(User user) {
        this.currentUser = user;
    }

    public synchronized void logout() {
        this.currentUser = null;
    }

    public synchronized boolean isLoggedIn() {
        return currentUser != null;
    }

    public synchronized User requireUser() {
        if (currentUser == null) {
            throw new IllegalStateException("No user is logged in");
        }
        return currentUser;
    }
}
