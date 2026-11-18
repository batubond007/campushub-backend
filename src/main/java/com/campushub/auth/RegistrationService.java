package com.campushub.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campushub.auth.otp.OtpService;
import com.campushub.common.error.ApiException;
import com.campushub.common.error.ErrorCode;
import com.campushub.config.CampushubProperties;
import com.campushub.user.AccountStatus;
import com.campushub.user.Emails;
import com.campushub.user.Role;
import com.campushub.user.User;
import com.campushub.user.UserRepository;

/** US-01: sign up with a university e-mail; a verification code is sent right away (checked in US-02). */
@Service
public class RegistrationService {

	private final UserRepository users;

	private final OtpService otps;

	private final PasswordEncoder passwordEncoder;

	private final List<String> allowedDomains;

	private final Clock clock;

	public RegistrationService(UserRepository users, OtpService otps, PasswordEncoder passwordEncoder,
			CampushubProperties properties, Clock clock) {
		this.users = users;
		this.otps = otps;
		this.passwordEncoder = passwordEncoder;
		this.allowedDomains = properties.allowedDomains();
		this.clock = clock;
	}

	/**
	 * Creates the account (or updates a sign-up that is still waiting for verification) and sends a code.
	 * One transaction: if the code cannot be sent yet (cooldown), nothing is changed.
	 */
	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		String email = Emails.normalize(request.email());
		if (!allowedDomains.contains(Emails.domain(email))) {
			String detail = "Please sign up with your university e-mail address (@" + String.join(", @", allowedDomains)
					+ ").";
			throw new ApiException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED, detail, Map.of("email", detail));
		}

		String fullName = request.fullName().trim();
		String passwordHash = passwordEncoder.encode(request.password());
		User user = users.findByEmail(email).orElse(null);
		if (user != null && user.getStatus() != AccountStatus.PENDING_VERIFICATION) {
			throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this e-mail already exists. Please log in.");
		}
		if (user == null) {
			user = users.save(new User(email, passwordHash, fullName, Role.STUDENT, AccountStatus.PENDING_VERIFICATION,
					clock.instant()));
		}
		else {
			// Signing up again before verifying: keep the account, take the new name and password.
			user.setFullName(fullName);
			user.setPasswordHash(passwordHash);
		}

		Duration validFor = otps.issue(user);
		return new RegisterResponse(email, "We sent a verification code to " + email + ".", validFor.toMinutes());
	}

}
