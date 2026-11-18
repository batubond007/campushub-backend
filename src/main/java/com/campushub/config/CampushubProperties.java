package com.campushub.config;

import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All {@code campushub.*} settings (see application.properties). On the server they are overridden through
 * environment variables in {@code /opt/campushub/app.env}, e.g. {@code CAMPUSHUB_JWT_SECRET}.
 *
 * @param allowedDomains e-mail domains that may sign up; exact match only (no subdomains)
 */
@ConfigurationProperties("campushub")
public record CampushubProperties(@DefaultValue Jwt jwt, @DefaultValue("metu.edu.tr") List<String> allowedDomains,
		@DefaultValue Otp otp, @DefaultValue DemoData demoData) {

	/**
	 * @param secret HMAC key for signing access tokens (HS256), at least 32 bytes; set {@code CAMPUSHUB_JWT_SECRET} in production
	 * @param accessTokenTtl how long an access token works
	 */
	public record Jwt(String secret, @DefaultValue("15m") Duration accessTokenTtl) {
	}

	/**
	 * E-mail verification codes (US-02).
	 *
	 * @param fixedCode when set (e.g. {@code 123456}), every code is this value, so the app can be tried without
	 * e-mail; leave empty to use random codes
	 * @param ttl how long a code works
	 * @param maxAttempts wrong tries before a new code is needed
	 * @param resendCooldown minimum time between two codes for the same account
	 */
	public record Otp(@Nullable String fixedCode, @DefaultValue("10m") Duration ttl, @DefaultValue("5") int maxAttempts,
			@DefaultValue("60s") Duration resendCooldown) {

		public boolean hasFixedCode() {
			return fixedCode != null && !fixedCode.isBlank();
		}

	}

	/** @param enabled create the ready-made demo accounts (and later demo events) on start-up */
	public record DemoData(@DefaultValue("false") boolean enabled) {
	}

}
