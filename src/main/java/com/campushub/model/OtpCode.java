package com.campushub.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The one active verification code of an account (table {@code otp_codes}, see V2__otp_codes.sql). */
@Entity
@Table(name = "otp_codes")
public class OtpCode {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "code_hash", nullable = false, length = 64)
	private String codeHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	protected OtpCode() {
	}

	public OtpCode(Long userId, String codeHash, Instant expiresAt, Instant sentAt) {
		this.userId = userId;
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
		this.sentAt = sentAt;
		this.attempts = 0;
	}

	public Long getUserId() {
		return userId;
	}

	public String getCodeHash() {
		return codeHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public int getAttempts() {
		return attempts;
	}

	public void addAttempt() {
		attempts++;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	/** Re-use this row for a new code (the old code stops working). */
	public void replace(String codeHash, Instant expiresAt, Instant sentAt) {
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
		this.sentAt = sentAt;
		this.attempts = 0;
	}

}
