package com.fraudshield.cases.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AnalystRepository analysts;
    private final BCryptPasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(AnalystRepository analysts, BCryptPasswordEncoder encoder, JwtService jwt) {
        this.analysts = analysts;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        return analysts.find(req.username().trim().toLowerCase())
                .filter(a -> encoder.matches(req.password(), a.passwordHash()))
                .<ResponseEntity<?>>map(a -> ResponseEntity.ok(jwt.issue(a)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid username or password")));
    }

    /** The gateway has already verified the token and forwards the identity as headers. */
    @GetMapping("/me")
    public Map<String, String> me(@RequestHeader("X-User-Id") String id,
                                  @RequestHeader(value = "X-User-Name", defaultValue = "") String name,
                                  @RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
        return Map.of("username", id, "displayName", name, "role", role);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    @org.springframework.context.annotation.Configuration
    static class Crypto {
        @Bean
        BCryptPasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
