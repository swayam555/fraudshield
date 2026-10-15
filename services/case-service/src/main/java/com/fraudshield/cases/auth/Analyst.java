package com.fraudshield.cases.auth;

public record Analyst(String username, String displayName, String passwordHash, String role) {
}
