package com.fraudshield.cases.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Seeds the demo accounts. Passwords are stored as BCrypt hashes, never in plain text. */
@Component
public class AnalystSeeder implements ApplicationRunner {

    private final AnalystRepository analysts;
    private final BCryptPasswordEncoder encoder;

    public AnalystSeeder(AnalystRepository analysts, BCryptPasswordEncoder encoder) {
        this.analysts = analysts;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        analysts.insertIfMissing(new Analyst("analyst", "Priya Raman", encoder.encode("fraudshield"), "ANALYST"));
        analysts.insertIfMissing(new Analyst("lead", "Arjun Mehta", encoder.encode("fraudshield"), "LEAD"));
    }
}
