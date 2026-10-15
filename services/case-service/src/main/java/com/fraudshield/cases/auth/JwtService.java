package com.fraudshield.cases.auth;

import com.fraudshield.cases.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Issues the HS256 tokens that the API gateway verifies on every request. */
@Service
public class JwtService {

    private final SecretKey key;
    private final AppProperties props;

    public JwtService(AppProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public Token issue(Analyst analyst) {
        Instant now = Instant.now();
        Instant expires = now.plus(props.jwt().ttl());
        String jwt = Jwts.builder()
                .subject(analyst.username())
                .claim("name", analyst.displayName())
                .claim("role", analyst.role())
                .issuer("fraudshield")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
        return new Token(jwt, expires, analyst.username(), analyst.displayName(), analyst.role());
    }

    public record Token(String accessToken, Instant expiresAt, String username, String displayName, String role) {
    }
}
