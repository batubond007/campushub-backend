package com.campushub.auth.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.campushub.config.CampushubProperties;
import com.campushub.user.User;

/**
 * Issues access tokens: JWTs with {@code sub} = user id, {@code email}, {@code role}, valid 15 minutes.
 * They are checked without the database (see SecurityConfig), which is why they are short-lived.
 */
@Service
public class AccessTokenService {

	public record AccessToken(String value, Instant expiresAt) {
	}

	private final JwtEncoder encoder;

	private final Duration ttl;

	private final Clock clock;

	public AccessTokenService(JwtEncoder encoder, CampushubProperties properties, Clock clock) {
		this.encoder = encoder;
		this.ttl = properties.jwt().accessTokenTtl();
		this.clock = clock;
	}

	public AccessToken issue(User user) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(ttl);
		JwtClaimsSet claims = JwtClaimsSet.builder()
			// A unique id per token: two tokens issued in the same second are still different.
			.id(UUID.randomUUID().toString())
			.subject(user.getId().toString())
			.claim("email", user.getEmail())
			.claim("role", user.getRole().name())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return new AccessToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
	}

}
