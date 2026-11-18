package com.campushub.demo;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.campushub.auth.otp.OtpService;
import com.campushub.event.EventRepository;
import com.campushub.user.AccountStatus;
import com.campushub.user.Role;
import com.campushub.user.User;
import com.campushub.user.UserRepository;

/**
 * Ready-made accounts and events for trying the app, the same as the frontend mock (password {@code Demo1234}).
 * Only with {@code campushub.demo-data.enabled=true}. Existing accounts are left alone, and events are only added
 * when there is no upcoming event at all, so restarts are safe and a long-running demo never runs empty.
 */
@Component
@ConditionalOnBooleanProperty("campushub.demo-data.enabled")
class DemoData implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoData.class);

	static final String PASSWORD = "Demo1234";

	private final UserRepository users;

	private final OtpService otps;

	private final PasswordEncoder passwordEncoder;

	private final EventRepository events;

	private final Clock clock;

	DemoData(UserRepository users, OtpService otps, PasswordEncoder passwordEncoder, EventRepository events,
			Clock clock) {
		this.users = users;
		this.otps = otps;
		this.passwordEncoder = passwordEncoder;
		this.events = events;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		addUser("ayse.yilmaz@metu.edu.tr", "Ayşe Yılmaz", Role.STUDENT, AccountStatus.ACTIVE);
		User burak = addUser("burak.celik@metu.edu.tr", "Burak Çelik", Role.STUDENT, AccountStatus.PENDING_VERIFICATION);
		if (burak != null) {
			// "Already got" a code, so 123456 works right away and "Send a new code" is not blocked.
			otps.seedForDemo(burak, "123456");
		}
		addUser("zeynep.demir@metu.edu.tr", "Zeynep Demir", Role.UNIVERSITY_ADMIN, AccountStatus.ACTIVE);

		if (events.findByEndTimeAfter(clock.instant(), PageRequest.of(0, 1)).isEmpty()) {
			User admin = users.findByEmail("zeynep.demir@metu.edu.tr").orElseThrow();
			var demoEvents = DemoEvents.create(admin.getId(), clock);
			log.info("Demo data: creating {} events", demoEvents.size());
			events.saveAll(demoEvents);
		}
	}

	/** @return the new user, or null when the account already exists */
	private User addUser(String email, String fullName, Role role, AccountStatus status) {
		if (users.findByEmail(email).isPresent()) {
			return null;
		}
		log.info("Demo data: creating {} ({})", email, role);
		return users.save(new User(email, passwordEncoder.encode(PASSWORD), fullName, role, status, clock.instant()));
	}

}
