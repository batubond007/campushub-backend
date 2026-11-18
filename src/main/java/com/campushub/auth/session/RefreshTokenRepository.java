package com.campushub.auth.session;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** Ends every session of a user (log-out, or a replayed token). */
	@Modifying
	@Query("delete from RefreshToken t where t.userId = :userId")
	int deleteAllByUserId(Long userId);

	/** Housekeeping: expired tokens are useless, also for replay detection. */
	@Modifying
	@Query("delete from RefreshToken t where t.userId = :userId and t.expiresAt < :now")
	int deleteExpired(Long userId, Instant now);

}
