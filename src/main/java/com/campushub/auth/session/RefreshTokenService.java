package com.campushub.auth.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campushub.common.error.ApiException;
import com.campushub.common.error.ErrorCode;
import com.campushub.common.error.Problems;
import com.campushub.config.CampushubProperties;
import com.campushub.user.AccountStatus;
import com.campushub.user.User;
import com.campushub.user.UserRepository;

/**
 * Refresh tokens: random 256-bit values, stored only as SHA-256 hashes.
 * <ul>
 * <li>Rotation: every refresh uses the token up and hands out a new one.</li>
 * <li>Replay detection: an already used token coming back means it was copied, so every session of that user
 * ends (the real owner just logs in again).</li>
 * <li>Log-out removes all of the user's tokens: the session ends on every device.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository tokens;

	private final UserRepository users;

	private final Duration ttl;

	private final Clock clock;

	public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, CampushubProperties properties,
			Clock clock) {
		this.tokens = tokens;
		this.users = users;
		this.ttl = properties.auth().refreshTokenTtl();
		this.clock = clock;
	}

	/** @return the raw token for the cookie; only its hash is stored */
	@Transactional
	public String create(User user) {
		Instant now = clock.instant();
		tokens.deleteExpired(user.getId(), now);
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		tokens.save(new RefreshToken(hash(raw), user.getId(), now.plus(ttl), now));
		return raw;
	}

	/**
	 * Uses a refresh token up and returns its (still active) user; the caller then issues new tokens.
	 * Does not roll back on ApiException, so a replay still ends the user's sessions.
	 *
	 * @throws ApiException UNAUTHENTICATED when the token is unknown, expired, already used, or the account is no
	 * longer active
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public User consume(String raw) {
		RefreshToken token = tokens.findByTokenHash(hash(raw)).orElseThrow(Problems::unauthenticated);
		if (token.isUsed()) {
			log.warn("Refresh token replayed for user {}: ending all of their sessions", token.getUserId());
			tokens.deleteAllByUserId(token.getUserId());
			throw new ApiException(ErrorCode.UNAUTHENTICATED,
					"Your session has ended for security reasons. Please log in again.");
		}
		if (!clock.instant().isBefore(token.getExpiresAt())) {
			throw Problems.unauthenticated();
		}
		User user = users.findById(token.getUserId()).orElseThrow(Problems::unauthenticated);
		if (user.getStatus() != AccountStatus.ACTIVE) {
			tokens.deleteAllByUserId(user.getId());
			throw Problems.unauthenticated();
		}
		token.markUsed();
		return user;
	}

	/** The user a refresh token belongs to, if it is known (used for log-out). */
	@Transactional(readOnly = true)
	public Optional<Long> ownerOf(String raw) {
		return tokens.findByTokenHash(hash(raw)).map(RefreshToken::getUserId);
	}

	@Transactional
	public void revokeAll(Long userId) {
		tokens.deleteAllByUserId(userId);
	}

	static String hash(String raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
