package com.campushub.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** {@code POST /api/auth/verify} body ({@code VerifyRequest} in the frontend). */
public record VerifyRequest(@NotBlank(message = "E-mail is required.") String email,
		@NotBlank(message = "The code has 6 digits.") @Pattern(regexp = "\\d{6}", message = "The code has 6 digits.") String code) {
}
