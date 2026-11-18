package com.campushub.common.error;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable reasons sent in the {@code code} member of every error body.
 * <p>
 * Part of the API contract: this list must stay identical to {@code src/lib/errorCodes.ts} in
 * campushub-frontend. The frontend decides what to do from the HTTP status and this code, never from the
 * {@code detail} text.
 */
public enum ErrorCode {

	// Any endpoint
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

	// Sign up / log in (US-01, US-03)
	EMAIL_DOMAIN_NOT_ALLOWED(HttpStatus.BAD_REQUEST),
	EMAIL_TAKEN(HttpStatus.CONFLICT),
	BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
	EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
	ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),

	// E-mail verification (US-02)
	SIGNUP_NOT_FOUND(HttpStatus.BAD_REQUEST),
	ALREADY_VERIFIED(HttpStatus.CONFLICT),
	OTP_NOT_FOUND(HttpStatus.BAD_REQUEST),
	OTP_EXPIRED(HttpStatus.BAD_REQUEST),
	OTP_INVALID(HttpStatus.BAD_REQUEST),
	OTP_TOO_MANY_ATTEMPTS(HttpStatus.BAD_REQUEST),
	OTP_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}

}
