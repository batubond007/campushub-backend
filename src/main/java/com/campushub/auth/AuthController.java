package com.campushub.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** {@code /api/auth/*}: open endpoints for signing up, verifying, logging in and out (see SecurityConfig). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final RegistrationService registration;

	public AuthController(RegistrationService registration) {
		this.registration = registration;
	}

	/** US-01. Errors: VALIDATION_FAILED, EMAIL_DOMAIN_NOT_ALLOWED, EMAIL_TAKEN (409), OTP_COOLDOWN (429). */
	@PostMapping("/register")
	public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
		return registration.register(request);
	}

}
