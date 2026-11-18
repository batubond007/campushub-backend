package com.campushub.auth;

/** {@code POST /api/auth/register} answer ({@code RegisterResponse} in the frontend). */
public record RegisterResponse(String email, String message, long expiresInMinutes) {
}
