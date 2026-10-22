package com.afrismart.identity;

import com.afrismart.shared.security.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 32) String phone,
        @NotNull Role role
) {
}
