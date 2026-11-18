package com.campushub.user;

/** Same values as {@code AccountStatus} in the frontend's {@code src/features/auth/types.ts}. */
public enum AccountStatus {

	/** Signed up, e-mail not verified yet (US-02); cannot log in. */
	PENDING_VERIFICATION,

	ACTIVE,

	DISABLED

}
