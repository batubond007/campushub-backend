package com.campushub.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

	/** Services read "now" from this clock, so tests can move time (code expiry, cooldowns). */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	/** BCrypt: slow on purpose, salted. It only uses the first 72 bytes of a password, so longer ones are refused. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
