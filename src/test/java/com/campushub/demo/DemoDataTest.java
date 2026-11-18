package com.campushub.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.campushub.auth.otp.OtpCodeRepository;
import com.campushub.user.AccountStatus;
import com.campushub.user.Role;
import com.campushub.user.User;
import com.campushub.user.UserRepository;

@SpringBootTest(properties = "campushub.demo-data.enabled=true")
class DemoDataTest {

	@Autowired
	private UserRepository users;

	@Autowired
	private OtpCodeRepository codes;

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
	void runningAgainChangesNothing() {
		long before = users.count();
		demoData.run(null);
		assertThat(users.count()).isEqualTo(before);
	}

}
