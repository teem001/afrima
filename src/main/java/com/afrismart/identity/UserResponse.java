package com.afrismart.identity;

import java.util.UUID;

import com.afrismart.shared.security.Role;

public record UserResponse(UUID id, String email, String fullName, String phone, Role role) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getRole());
    }
}
