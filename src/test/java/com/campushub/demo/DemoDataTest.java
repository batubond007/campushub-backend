package com.campushub.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.campushub.model.AccountStatus;
import com.campushub.model.Role;
import com.campushub.model.User;
import com.campushub.repository.EventRepository;
import com.campushub.repository.OtpCodeRepository;
import com.campushub.repository.UserRepository;

@SpringBootTest(properties = "campushub.demo-data.enabled=true")
class DemoDataTest {

	@Autowired
	private UserRepository users;

	@Autowired
	private OtpCodeRepository codes;

	@Autowired
	private EventRepository events;

	@Autowired
	private DemoData demoData;

	@Test
	void createsTheMockAccounts() {
		assertThat(users.findByEmail("ayse.yilmaz@metu.edu.tr")).get()
			.extracting(User::getStatus)
			.isEqualTo(AccountStatus.ACTIVE);
		User burak = users.findByEmail("burak.celik@metu.edu.tr").orElseThrow();
		assertThat(burak.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
		assertThat(codes.findById(burak.getId())).isPresent();
		assertThat(users.findByEmail("zeynep.demir@metu.edu.tr")).get()
			.extracting(User::getRole)
			.isEqualTo(Role.UNIVERSITY_ADMIN);
	}

	@Test
	void createsTheMockEvents() {
		// 19 upcoming + 1 that has already ended, like mock/eventsMock.ts
		assertThat(events.count()).isEqualTo(20);
		assertThat(events.findByEndTimeAfter(Instant.now(), PageRequest.of(0, 50)).getTotalElements()).isEqualTo(19);
	}

	@Test
	void runningAgainChangesNothing() {
		long usersBefore = users.count();
		long eventsBefore = events.count();
		demoData.run(null);
		assertThat(users.count()).isEqualTo(usersBefore);
		assertThat(events.count()).isEqualTo(eventsBefore);
	}

}
