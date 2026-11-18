package com.campushub.auth.session;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.campushub.config.CampushubProperties;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The refresh-token cookie: {@code HttpOnly} (JavaScript cannot read it), {@code SameSite=Strict} (other sites
 * cannot make the browser send it), {@code Path=/api/auth} (only sent to refresh and log-out), {@code Secure}
 * from settings (off while the site is plain HTTP).
 */
@Component
public class RefreshCookies {

	private final CampushubProperties.RefreshCookie settings;

	private final long maxAgeSeconds;

	public RefreshCookies(CampushubProperties properties) {
		this.settings = properties.auth().refreshCookie();
		this.maxAgeSeconds = properties.auth().refreshTokenTtl().toSeconds();
	}

	public String name() {
		return settings.name();
	}

	public void set(HttpServletResponse response, String token) {
		response.addHeader(HttpHeaders.SET_COOKIE, build(token, maxAgeSeconds));
	}

	/** Tells the browser to delete the cookie. */
	public void clear(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, build("", 0));
	}

	private String build(String value, long maxAge) {
		return ResponseCookie.from(settings.name(), value)
			.httpOnly(true)
			.secure(settings.secure())
			.sameSite("Strict")
			.path(settings.path())
			.maxAge(maxAge)
			.build()
			.toString();
	}

}
