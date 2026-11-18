package com.campushub.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Signs access tokens in tests with the application's own {@link JwtEncoder}. */
public final class TestTokens {

	private TestTokens() {
	}

	public static String accessToken(JwtEncoder encoder, long userId, String role) {
		return accessToken(encoder, userId, role, Instant.now().plus(15, ChronoUnit.MINUTES));
	}

	public static String accessToken(JwtEncoder encoder, long userId, String role, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(Long.toString(userId))
			.claim("role", role)
			.issuedAt(expiresAt.minus(15, ChronoUnit.MINUTES))
			.expiresAt(expiresAt)
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public static String bearer(String token) {
		return "Bearer " + token;
	}

}
