package com.campushub.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import com.campushub.dto.AuthSession;
import com.campushub.dto.LoginRequest;
import com.campushub.exception.ApiException;
import com.campushub.exception.Problems;
import com.campushub.security.RefreshCookies;
import com.campushub.service.SessionService.NewSession;
import com.campushub.service.SessionService;

/**
 * US-03 sessions. Log-in returns the access token in the body and sets the refresh cookie; refresh and log-out
 * need only that cookie (no access token), so they still work after the access token has expired.
 */
@RestController
@RequestMapping("/api/auth")
public class SessionController {

	private final SessionService sessions;

	private final RefreshCookies cookies;

	public SessionController(SessionService sessions, RefreshCookies cookies) {
		this.sessions = sessions;
		this.cookies = cookies;
	}

	/** Errors: 401 BAD_CREDENTIALS, 403 EMAIL_NOT_VERIFIED, 403 ACCOUNT_DISABLED. */
	@PostMapping("/login")
	public AuthSession login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
		return respond(sessions.login(request.email(), request.password()), response);
	}

	/** Error: 401 UNAUTHENTICATED (the session has ended); the cookie is then cleared too. */
	@PostMapping("/refresh")
	public AuthSession refresh(HttpServletRequest request, HttpServletResponse response) {
		String token = refreshToken(request);
		try {
			if (token == null) {
				throw Problems.unauthenticated();
			}
			return respond(sessions.refresh(token), response);
		}
		catch (ApiException ex) {
			// Headers set before the error answer is written are kept, so the browser drops the dead cookie.
			cookies.clear(response);
			throw ex;
		}
	}

	/** Always 204: ends every session of the cookie's owner (all devices) and clears the cookie. */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		sessions.logout(refreshToken(request));
		cookies.clear(response);
		return ResponseEntity.noContent().build();
	}

	private AuthSession respond(NewSession session, HttpServletResponse response) {
		cookies.set(response, session.refreshToken());
		return session.body();
	}

	private String refreshToken(HttpServletRequest request) {
		Cookie cookie = WebUtils.getCookie(request, cookies.name());
		return (cookie == null || cookie.getValue().isBlank()) ? null : cookie.getValue();
	}

}
