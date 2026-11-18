package com.campushub.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All {@code campushub.*} settings (see application.properties). On the server they are overridden through
 * environment variables in {@code /opt/campushub/app.env}, e.g. {@code CAMPUSHUB_JWT_SECRET}.
 */
@ConfigurationProperties("campushub")
public record CampushubProperties(@DefaultValue Jwt jwt) {

	/**
	 * @param secret HMAC key for signing access tokens (HS256), at least 32 bytes; set {@code CAMPUSHUB_JWT_SECRET} in production
	 * @param accessTokenTtl how long an access token works
	 */
	public record Jwt(String secret, @DefaultValue("15m") Duration accessTokenTtl) {
	}

}
