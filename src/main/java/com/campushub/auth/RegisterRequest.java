package com.campushub.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/auth/register} body ({@code RegisterRequest} in the frontend). Field names = fieldErrors keys. */
public record RegisterRequest(
		@NotBlank(message = "Full name is required.")
		@Size(max = 100, message = "Full name can be at most 100 characters.") String fullName,
		@NotBlank(message = "E-mail is required.")
		@Email(message = "Please enter a valid e-mail address.")
		@Size(max = 254, message = "E-mail is too long.") String email,
		@NotBlank(message = "Password is required.") @StrongPassword String password) {
}
