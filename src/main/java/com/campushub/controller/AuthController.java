package com.campushub.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.campushub.dto.MessageResponse;
import com.campushub.dto.RegisterRequest;
import com.campushub.dto.RegisterResponse;
import com.campushub.dto.ResendCodeRequest;
import com.campushub.dto.VerifyRequest;
import com.campushub.service.RegistrationService;
import com.campushub.service.VerificationService;

/** {@code /api/auth/*}: open endpoints for signing up, verifying, logging in and out (see SecurityConfig). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final RegistrationService registration;

	private final VerificationService verification;

	public AuthController(RegistrationService registration, VerificationService verification) {
		this.registration = registration;
		this.verification = verification;
	}

	/** US-01. Errors: VALIDATION_FAILED, EMAIL_DOMAIN_NOT_ALLOWED, EMAIL_TAKEN (409), OTP_COOLDOWN (429). */
	@PostMapping("/register")
	public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
		return registration.register(request);
	}

	/**
	 * US-02. Errors: VALIDATION_FAILED (fieldErrors.code), SIGNUP_NOT_FOUND, ALREADY_VERIFIED (409),
	 * OTP_NOT_FOUND, OTP_EXPIRED, OTP_INVALID, OTP_TOO_MANY_ATTEMPTS.
	 */
	@PostMapping("/verify")
	public MessageResponse verify(@Valid @RequestBody VerifyRequest request) {
		return verification.verify(request);
	}

	/** US-02. Errors: SIGNUP_NOT_FOUND, ALREADY_VERIFIED (409), OTP_COOLDOWN (429). */
	@PostMapping("/resend-otp")
	public MessageResponse resendCode(@Valid @RequestBody ResendCodeRequest request) {
		return verification.resend(request);
	}

}
