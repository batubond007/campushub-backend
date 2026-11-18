package com.campushub.auth.otp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campushub.common.error.ApiException;
import com.campushub.common.error.ErrorCode;
import com.campushub.config.CampushubProperties;
import com.campushub.user.User;

/** Creates and sends the 6-digit e-mail verification codes (US-02). */
@Service
public class OtpService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final OtpCodeRepository codes;

	private final OtpSender sender;

	private final CampushubProperties.Otp settings;

	private final Clock clock;

	public OtpService(OtpCodeRepository codes, OtpSender sender, CampushubProperties properties, Clock clock) {
		this.codes = codes;
		this.sender = sender;
		this.settings = properties.otp();
		this.clock = clock;
	}

	/**
	 * Sends a new code to the user; the previous code stops working.
	 *
	 * @return how long the new code is valid
	 * @throws ApiException {@code OTP_COOLDOWN} when the last code was sent less than the cooldown ago
	 */
	@Transactional
	public Duration issue(User user) {
		Instant now = clock.instant();
		OtpCode existing = codes.findById(user.getId()).orElse(null);
		if (existing != null) {
			Instant allowedAt = existing.getSentAt().plus(settings.resendCooldown());
			if (now.isBefore(allowedAt)) {
				long wait = Math.max(1, Duration.between(now, allowedAt).toSeconds());
				throw new ApiException(ErrorCode.OTP_COOLDOWN,
						"Please wait " + wait + " seconds before requesting a new code.");
			}
		}

		String code = settings.hasFixedCode() ? settings.fixedCode() : randomCode();
		Instant expiresAt = now.plus(settings.ttl());
		if (existing == null) {
			codes.save(new OtpCode(user.getId(), hash(code), expiresAt, now));
		}
		else {
			existing.replace(hash(code), expiresAt, now);
		}
		sender.send(user.getEmail(), code, settings.ttl());
		return settings.ttl();
	}

	/**
	 * Demo data only: gives an account a code that never expires and can be re-sent at once
	 * (like the mock's "burak" account), without sending anything.
	 */
	@Transactional
	public void seedForDemo(User user, String code) {
		codes.save(new OtpCode(user.getId(), hash(code), Instant.parse("9999-12-31T00:00:00Z"), Instant.EPOCH));
	}

	static String randomCode() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

	static String hash(String code) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
