package com.campushub.dto;

import java.time.Instant;


/**
 * Answer body of {@code POST /api/auth/login} and {@code /refresh} ({@code AuthSession} in the frontend).
 * The refresh token is not here: it goes into the httpOnly cookie.
 */
public record AuthSession(String accessToken, Instant expiresAt, Profile user) {
}
