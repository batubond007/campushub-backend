package com.campushub.user;

import java.time.Instant;
import java.util.List;

/** The user as the frontend sees it ({@code Profile} in {@code src/features/auth/types.ts}). Never contains the password hash. */
public record Profile(Long id, String email, String fullName, String department, String bio, List<String> interests,
		Role role, AccountStatus status, Instant createdAt) {

	public static Profile of(User user) {
		// Interests have no storage yet (no screen edits them); the field is already part of the contract.
		return new Profile(user.getId(), user.getEmail(), user.getFullName(), user.getDepartment(), user.getBio(),
				List.of(), user.getRole(), user.getStatus(), user.getCreatedAt());
	}

}
