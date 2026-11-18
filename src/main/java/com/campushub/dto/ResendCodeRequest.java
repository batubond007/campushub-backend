package com.campushub.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/auth/resend-otp} body. */
public record ResendCodeRequest(@NotBlank(message = "E-mail is required.") String email) {
}
