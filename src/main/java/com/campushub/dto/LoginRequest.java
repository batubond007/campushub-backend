package com.campushub.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/auth/login} body ({@code LoginRequest} in the frontend). */
public record LoginRequest(@NotBlank(message = "Please enter your e-mail address.") String email,
		@NotBlank(message = "Please enter your password.") String password) {
}
