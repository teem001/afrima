package com.afrismart.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.afrismart.shared.config.AfriSmartProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final AfriSmartProperties properties;
    private final SecretKey key;

    public JwtService(AfriSmartProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(UUID userId, String email, Role role) {
        Instant now = Instant.now();
        Instant expires = now.plus(properties.getJwt().getTtl());
        return Jwts.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return properties.getJwt().getTtl().toSeconds();
    }

    public AuthUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AuthUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class))
            );
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
