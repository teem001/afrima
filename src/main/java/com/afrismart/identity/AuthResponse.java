package com.afrismart.identity;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {
}
