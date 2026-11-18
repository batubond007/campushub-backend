package com.campushub.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Access tokens are JWTs signed with one shared secret (HS256): the backend both issues and checks them,
 * so no key pair or external identity provider is needed.
 */
@Configuration
public class JwtConfig {

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	static final String DEV_SECRET_PREFIX = "dev-only";

	@Bean
	SecretKey jwtSigningKey(CampushubProperties properties) {
		String secret = properties.jwt().secret();
		byte[] bytes = (secret == null) ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("campushub.jwt.secret (CAMPUSHUB_JWT_SECRET) must be at least 32 bytes long");
		}
		if (secret.startsWith(DEV_SECRET_PREFIX)) {
			log.warn("Using the development JWT secret. Set CAMPUSHUB_JWT_SECRET on the server.");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
		return NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
	}

}
