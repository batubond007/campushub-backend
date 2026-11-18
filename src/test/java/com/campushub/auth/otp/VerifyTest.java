package com.campushub.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.campushub.user.AccountStatus;
import com.campushub.user.Role;
import com.campushub.user.User;
import com.campushub.user.UserRepository;

/** COD-10 / US-02: POST /api/auth/verify and /api/auth/resend-otp (fixed test code 123456). */
@SpringBootTest
@AutoConfigureMockMvc
class VerifyTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private OtpCodeRepository codes;

	@Autowired
	private OtpService otps;

	/** A sign-up waiting for verification, with a fresh code 123456. */
	private User pending(String email) {
		User user = users.save(new User(email, "x", "Test User", Role.STUDENT, AccountStatus.PENDING_VERIFICATION,
				Instant.now()));
		otps.issue(user);
		return user;
	}

	private ResultActions verify(String email, String code) throws Exception {
		return mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"%s\", \"code\": \"%s\"}".formatted(email, code)));
	}

	private ResultActions resend(String email) throws Exception {
		return mvc.perform(post("/api/auth/resend-otp").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"%s\"}".formatted(email)));
	}

	@Test
	void correctCodeActivatesTheAccount() throws Exception {
		User user = pending("ok@metu.edu.tr");
		verify("OK@metu.edu.tr", "123456").andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Your e-mail is verified. You can log in now."));
		assertThat(users.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(codes.findById(user.getId())).isEmpty();

		verify("ok@metu.edu.tr", "123456").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_VERIFIED"));
	}

	@Test
	void codeMustHaveSixDigits() throws Exception {
		verify("ok@metu.edu.tr", "12a").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors.code").value("The code has 6 digits."));
	}

	@Test
	void unknownEmailHasNoSignUp() throws Exception {
		verify("nobody@metu.edu.tr", "123456").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SIGNUP_NOT_FOUND"));
	}

	@Test
	void wrongCodesCountDownThenLockTheCode() throws Exception {
		pending("wrong@metu.edu.tr");
		for (int left = 4; left >= 1; left--) {
			verify("wrong@metu.edu.tr", "000000").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("OTP_INVALID"))
				.andExpect(jsonPath("$.detail").value("The code is not correct. Attempts left: " + left + "."));
		}
		verify("wrong@metu.edu.tr", "000000").andExpect(jsonPath("$.code").value("OTP_TOO_MANY_ATTEMPTS"));
		// Even the right code no longer works: a new one has to be requested.
		verify("wrong@metu.edu.tr", "123456").andExpect(jsonPath("$.code").value("OTP_TOO_MANY_ATTEMPTS"));
	}

	@Test
	void expiredCodeIsRefused() throws Exception {
		User user = pending("late@metu.edu.tr");
		OtpCode otp = codes.findById(user.getId()).orElseThrow();
		Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
		otp.replace(OtpService.hash("123456"), past, past);
		codes.save(otp);

		verify("late@metu.edu.tr", "123456").andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
	}

	@Test
	void resendRespectsTheCooldownAndResetsAttempts() throws Exception {
		User user = pending("resend@metu.edu.tr");
		resend("resend@metu.edu.tr").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("OTP_COOLDOWN"));

		verify("resend@metu.edu.tr", "000000").andExpect(jsonPath("$.code").value("OTP_INVALID"));
		OtpCode otp = codes.findById(user.getId()).orElseThrow();
		Instant past = Instant.now().minus(2, ChronoUnit.MINUTES);
		otp.replace(otp.getCodeHash(), otp.getExpiresAt(), past);
		codes.save(otp);

		resend("resend@metu.edu.tr").andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("We sent a new code."));
		assertThat(codes.findById(user.getId()).orElseThrow().getAttempts()).isZero();
	}

	@Test
	void resendForVerifiedOrUnknownAccounts() throws Exception {
		users.save(new User("done@metu.edu.tr", "x", "Done", Role.STUDENT, AccountStatus.ACTIVE, Instant.now()));
		resend("done@metu.edu.tr").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_VERIFIED"));
		resend("nobody@metu.edu.tr").andExpect(jsonPath("$.code").value("SIGNUP_NOT_FOUND"));
	}

}
