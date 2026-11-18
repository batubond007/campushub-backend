package com.campushub.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campushub.dto.AuthSession;
import com.campushub.dto.Profile;
import com.campushub.exception.ApiException;
import com.campushub.exception.ErrorCode;
import com.campushub.model.AccountStatus;
import com.campushub.model.User;
import com.campushub.repository.UserRepository;
import com.campushub.util.Emails;

/** US-03: log in, keep the session alive (refresh) and log out. */
@Service
public class SessionService {

	/** The body plus the new refresh token, which the controller puts into the httpOnly cookie. */
	public record NewSession(AuthSession body, String refreshToken) {
	}

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final AccessTokenService accessTokens;

	private final RefreshTokenService refreshTokens;

	/** Compared against when the e-mail is unknown, so both cases take the same time (no account probing). */
	private final String dummyHash;

	public SessionService(UserRepository users, PasswordEncoder passwordEncoder, AccessTokenService accessTokens,
			RefreshTokenService refreshTokens) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.accessTokens = accessTokens;
		this.refreshTokens = refreshTokens;
		this.dummyHash = passwordEncoder.encode("not-a-real-password");
	}

	/**
	 * @throws ApiException BAD_CREDENTIALS (same for unknown e-mail and wrong password), EMAIL_NOT_VERIFIED,
	 * ACCOUNT_DISABLED
	 */
	@Transactional
	public NewSession login(String email, String password) {
		User user = users.findByEmail(Emails.normalize(email)).orElse(null);
		boolean matches = passwordEncoder.matches(password, (user != null) ? user.getPasswordHash() : dummyHash);
		if (user == null || !matches) {
			throw new ApiException(ErrorCode.BAD_CREDENTIALS, "E-mail or password is incorrect.");
		}
		if (user.getStatus() == AccountStatus.PENDING_VERIFICATION) {
			throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED, "Please verify your e-mail address before logging in.");
		}
		if (user.getStatus() == AccountStatus.DISABLED) {
			throw new ApiException(ErrorCode.ACCOUNT_DISABLED,
					"This account has been disabled. Please contact the administrator.");
		}
		return start(user);
	}

	/** Swaps a refresh token for a new access token and a new refresh token (rotation). */
	@Transactional(noRollbackFor = ApiException.class)
	public NewSession refresh(String refreshToken) {
		return start(refreshTokens.consume(refreshToken));
	}

	/** Ends every session of the token's owner. Unknown or missing tokens are fine: log-out always succeeds. */
	@Transactional
	public void logout(String refreshToken) {
		if (refreshToken != null) {
			refreshTokens.ownerOf(refreshToken).ifPresent(refreshTokens::revokeAll);
		}
	}

	private NewSession start(User user) {
		AccessTokenService.AccessToken access = accessTokens.issue(user);
		String refresh = refreshTokens.create(user);
		return new NewSession(new AuthSession(access.value(), access.expiresAt(), Profile.of(user)), refresh);
	}

}
