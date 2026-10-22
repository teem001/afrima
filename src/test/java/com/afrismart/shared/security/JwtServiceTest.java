package com.afrismart.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.afrismart.shared.config.AfriSmartProperties;

class JwtServiceTest {

    @Test
    void issueAndParseRoundTrip() {
        AfriSmartProperties properties = new AfriSmartProperties();
        properties.getJwt().setSecret("local-only-change-me-afrismart-jwt-hs256-key");
        JwtService jwtService = new JwtService(properties);
        UUID userId = UUID.randomUUID();

        String token = jwtService.issue(userId, "ada@afrismart.ng", Role.CONSUMER);
        AuthUser parsed = jwtService.parse(token);

        assertThat(parsed).isNotNull();
        assertThat(parsed.id()).isEqualTo(userId);
        assertThat(parsed.email()).isEqualTo("ada@afrismart.ng");
        assertThat(parsed.role()).isEqualTo(Role.CONSUMER);
    }

    @Test
    void invalidTokenReturnsNull() {
        AfriSmartProperties properties = new AfriSmartProperties();
        properties.getJwt().setSecret("local-only-change-me-afrismart-jwt-hs256-key");
        JwtService jwtService = new JwtService(properties);

        assertThat(jwtService.parse("not-a-jwt")).isNull();
    }
}
