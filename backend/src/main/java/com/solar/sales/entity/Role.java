package com.solar.sales.entity;

/**
 * PENDING: signed up, waiting for an admin to choose their role; no access except their own profile.
 * USER (sales): add sales, view sales/installations, update installation status.
 * AGENT (installer): only installation tasks they picked up or accepted.
 * ADMIN: everything incl. Excel upload, users, assigning tasks to agents.
 */
public enum Role {
    PENDING, USER, AGENT, ADMIN;

    /** The roles someone may ask for when signing up (the admin decides). */
    public boolean requestable() {
        return this == USER || this == AGENT;
    }
}
