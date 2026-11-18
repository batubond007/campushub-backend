package com.campushub.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	/** @param email already normalized (trimmed, lower-case), see {@link Emails#normalize(String)} */
	Optional<User> findByEmail(String email);

}
