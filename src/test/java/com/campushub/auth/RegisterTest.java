package com.campushub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.campushub.auth.otp.OtpCodeRepository;
import com.campushub.user.AccountStatus;
import com.campushub.user.Role;
import com.campushub.user.User;
import com.campushub.user.UserRepository;

/**
 * COD-9 / US-01: POST /api/auth/register, with the same answers as the frontend mock.
 * Not @Transactional on purpose: the service's own transaction must roll back a refused sign-up.
 * Every test uses its own e-mail address.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegisterTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private OtpCodeRepository codes;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private ResultActions register(String fullName, String email, String password) throws Exception {
		String body = """
				{"fullName": "%s", "email": "%s", "password": "%s"}""".formatted(fullName, email, password);
		return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void createsPendingAccountAndSendsCode() throws Exception {
		register("  Test User ", "Test.User@METU.edu.tr", "Demo1234")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("test.user@metu.edu.tr"))
			.andExpect(jsonPath("$.message").value("We sent a verification code to test.user@metu.edu.tr."))
			.andExpect(jsonPath("$.expiresInMinutes").value(10));

		User user = users.findByEmail("test.user@metu.edu.tr").orElseThrow();
		assertThat(user.getFullName()).isEqualTo("Test User");
		assertThat(user.getRole()).isEqualTo(Role.STUDENT);
		assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
		assertThat(passwordEncoder.matches("Demo1234", user.getPasswordHash())).isTrue();
		assertThat(codes.findById(user.getId())).isPresent();
	}

	@Test
	void weakPasswordIsAFieldError() throws Exception {
		register("Test User", "test.user@metu.edu.tr", "short")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors.password")
				.value("Password must be at least 8 characters with a letter and a number."));
	}

	@Test
	void passwordLongerThan72BytesIsRefused() throws Exception {
		// 40 Turkish letters are 40 characters but 80 bytes: BCrypt would silently cut them.
		register("Test User", "test.user@metu.edu.tr", "ş".repeat(40) + "1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.password").value("Password is too long. Please use a shorter one."));
	}

	@Test
	void missingFieldsAreFieldErrors() throws Exception {
		register("", "not-an-email", "Demo1234")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.fullName").value("Full name is required."))
			.andExpect(jsonPath("$.fieldErrors.email").value("Please enter a valid e-mail address."));
	}

	@Test
	void otherDomainsAndSubdomainsAreRefused() throws Exception {
		for (String email : new String[] { "a.b@gmail.com", "a.b@ceng.metu.edu.tr" }) {
			register("Test User", email, "Demo1234")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_DOMAIN_NOT_ALLOWED"))
				.andExpect(jsonPath("$.fieldErrors.email")
					.value("Please sign up with your university e-mail address (@metu.edu.tr)."));
		}
	}

	@Test
	void verifiedAccountCannotSignUpAgain() throws Exception {
		users.save(new User("taken@metu.edu.tr", "x", "Taken", Role.STUDENT, AccountStatus.ACTIVE,
				java.time.Instant.now()));
		register("Test User", "taken@metu.edu.tr", "Demo1234")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
	}

	@Test
	void signingUpAgainTooSoonHitsTheCodeCooldown() throws Exception {
		register("Test User", "again@metu.edu.tr", "Demo1234").andExpect(status().isOk());
		register("New Name", "again@metu.edu.tr", "Other1234")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("OTP_COOLDOWN"));
		// Nothing changed: the second sign-up was refused as a whole.
		assertThat(users.findByEmail("again@metu.edu.tr").orElseThrow().getFullName()).isEqualTo("Test User");
	}

}
