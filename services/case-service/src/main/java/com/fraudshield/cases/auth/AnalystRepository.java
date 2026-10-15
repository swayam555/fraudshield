package com.fraudshield.cases.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AnalystRepository {

    private final JdbcClient jdbc;

    public AnalystRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Analyst> find(String username) {
        return jdbc.sql("SELECT username, display_name, password_hash, role FROM analysts WHERE username = ?")
                .param(username)
                .query(Analyst.class)
                .optional();
    }

    public void insertIfMissing(Analyst a) {
        jdbc.sql("""
                        INSERT INTO analysts (username, display_name, password_hash, role) VALUES (?, ?, ?, ?)
                        ON CONFLICT (username) DO NOTHING""")
                .params(a.username(), a.displayName(), a.passwordHash(), a.role())
                .update();
    }
}
