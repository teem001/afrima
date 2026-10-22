package com.afrismart.identity;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.security.AuthUser;
import com.afrismart.shared.security.JwtService;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict(ErrorCodes.EMAIL_TAKEN, "An account already exists for this email");
        }
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone() == null ? null : request.phone().trim());
        user.setRole(request.role());
        users.save(user);
        return toAuth(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserAccount user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> ApiException.unauthorized(ErrorCodes.INVALID_CREDENTIALS, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized(ErrorCodes.INVALID_CREDENTIALS, "Invalid email or password");
        }
        return toAuth(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(AuthUser current) {
        UserAccount user = users.findById(current.id())
                .orElseThrow(() -> ApiException.unauthorized(ErrorCodes.INVALID_CREDENTIALS, "Account not found"));
        return UserResponse.from(user);
    }

    private AuthResponse toAuth(UserAccount user) {
        String token = jwtService.issue(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, "Bearer", jwtService.ttlSeconds(), UserResponse.from(user));
    }
}
