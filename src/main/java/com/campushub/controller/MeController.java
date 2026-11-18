package com.campushub.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campushub.dto.Profile;
import com.campushub.exception.Problems;
import com.campushub.model.AccountStatus;
import com.campushub.repository.UserRepository;
import com.campushub.security.CurrentUser;

/** {@code GET /api/me}: the signed-in user's own profile (needs an access token). */
@RestController
public class MeController {

	private final UserRepository users;

	public MeController(UserRepository users) {
		this.users = users;
	}

	/** 401 UNAUTHENTICATED also when the account was removed or is no longer active. */
	@GetMapping("/api/me")
	public Profile me(@AuthenticationPrincipal Jwt jwt) {
		return users.findById(CurrentUser.id(jwt))
			.filter((user) -> user.getStatus() == AccountStatus.ACTIVE)
			.map(Profile::of)
			.orElseThrow(Problems::unauthenticated);
	}

}
