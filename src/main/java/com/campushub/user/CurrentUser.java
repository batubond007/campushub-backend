package com.campushub.user;

import org.springframework.security.oauth2.jwt.Jwt;

import com.campushub.common.error.Problems;

/** Reads the signed-in user from the access token: {@code sub} is the user id (see AccessTokenService). */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static long id(Jwt jwt) {
		if (jwt == null) {
			throw Problems.unauthenticated();
		}
		try {
			return Long.parseLong(jwt.getSubject());
		}
		catch (NumberFormatException ex) {
			throw Problems.unauthenticated();
		}
	}

}
