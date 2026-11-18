package com.campushub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.campushub.model.AccountStatus;
import com.campushub.model.Role;
import com.campushub.model.User;
import com.campushub.repository.UserRepository;

import jakarta.servlet.http.Cookie;

/** COD-11 / US-03: log-in, refresh with rotation and replay detection, log-out, /api/me. */
@SpringBootTest
@AutoConfigureMockMvc
class SessionTest {

	private static final Pattern REFRESH_VALUE = Pattern.compile("campushub_refresh=([^;]*)");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private User user(String email, AccountStatus status) {
		return users.save(new User(email, passwordEncoder.encode("Demo1234"), "Test User", Role.STUDENT, status,
				Instant.now()));
	}

	private MvcResult login(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password))).andReturn();
	}

	private static String setCookie(MvcResult result) {
		return result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
	}

	private static Cookie refreshCookie(MvcResult result) {
		Matcher matcher = REFRESH_VALUE.matcher(setCookie(result));
		assertThat(matcher.find()).isTrue();
		return new Cookie("campushub_refresh", matcher.group(1));
	}

	private static String accessToken(MvcResult result) throws Exception {
		return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
	}

	private MvcResult refresh(Cookie cookie) throws Exception {
		var request = post("/api/auth/refresh");
		if (cookie != null) {
			request.cookie(cookie);
		}
		return mvc.perform(request).andReturn();
	}

	@Test
	void loginGivesAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
		user("login@metu.edu.tr", AccountStatus.ACTIVE);
		MvcResult result = login("Login@METU.edu.tr", "Demo1234");

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		mvc.perform(get("/api/me").header("Authorization", "Bearer " + accessToken(result)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("login@metu.edu.tr"))
			.andExpect(jsonPath("$.role").value("STUDENT"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.interests").isArray())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
		assertThat(result.getResponse().getContentAsString()).contains("\"expiresAt\"", "\"user\"");
		assertThat(setCookie(result)).contains("campushub_refresh=", "HttpOnly", "SameSite=Strict", "Path=/api/auth",
				"Max-Age=604800")
			.doesNotContain("Secure");
	}

	@Test
	void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
		user("pw@metu.edu.tr", AccountStatus.ACTIVE);
		for (MvcResult result : new MvcResult[] { login("pw@metu.edu.tr", "Wrong1234"), login("nobody@metu.edu.tr", "Demo1234") }) {
			assertThat(result.getResponse().getStatus()).isEqualTo(401);
			assertThat(result.getResponse().getContentAsString()).contains("\"code\":\"BAD_CREDENTIALS\"",
					"E-mail or password is incorrect.");
		}
	}

	@Test
	void unverifiedAndDisabledAccountsCannotLogIn() throws Exception {
		user("pending@metu.edu.tr", AccountStatus.PENDING_VERIFICATION);
		user("disabled@metu.edu.tr", AccountStatus.DISABLED);
		MvcResult pending = login("pending@metu.edu.tr", "Demo1234");
		assertThat(pending.getResponse().getStatus()).isEqualTo(403);
		assertThat(pending.getResponse().getContentAsString()).contains("\"code\":\"EMAIL_NOT_VERIFIED\"");
		MvcResult disabled = login("disabled@metu.edu.tr", "Demo1234");
		assertThat(disabled.getResponse().getStatus()).isEqualTo(403);
		assertThat(disabled.getResponse().getContentAsString()).contains("\"code\":\"ACCOUNT_DISABLED\"");
	}

	@Test
	void refreshRotatesAndAReplayEndsEverySession() throws Exception {
		user("rotate@metu.edu.tr", AccountStatus.ACTIVE);
		MvcResult login = login("rotate@metu.edu.tr", "Demo1234");
		Cookie first = refreshCookie(login);

		MvcResult refreshed = refresh(first);
		assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
		Cookie second = refreshCookie(refreshed);
		assertThat(second.getValue()).isNotEqualTo(first.getValue());
		assertThat(accessToken(refreshed)).isNotEqualTo(accessToken(login));

		// The first token comes back: it was copied. Every session of the user ends, including the new one.
		MvcResult replay = refresh(first);
		assertThat(replay.getResponse().getStatus()).isEqualTo(401);
		assertThat(replay.getResponse().getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"",
				"for security reasons");
		assertThat(setCookie(replay)).contains("campushub_refresh=;", "Max-Age=0");
		assertThat(refresh(second).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void refreshWithoutCookieIs401() throws Exception {
		MvcResult result = refresh(null);
		assertThat(result.getResponse().getStatus()).isEqualTo(401);
		assertThat(result.getResponse().getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
		assertThat(setCookie(result)).contains("Max-Age=0");
	}

	@Test
	void refreshStopsWorkingWhenTheAccountIsDisabled() throws Exception {
		User user = user("later-disabled@metu.edu.tr", AccountStatus.ACTIVE);
		Cookie cookie = refreshCookie(login("later-disabled@metu.edu.tr", "Demo1234"));
		user.setStatus(AccountStatus.DISABLED);
		users.save(user);
		assertThat(refresh(cookie).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void logoutEndsTheSessionOnEveryDevice() throws Exception {
		user("logout@metu.edu.tr", AccountStatus.ACTIVE);
		Cookie laptop = refreshCookie(login("logout@metu.edu.tr", "Demo1234"));
		Cookie phone = refreshCookie(login("logout@metu.edu.tr", "Demo1234"));

		MvcResult logout = mvc.perform(post("/api/auth/logout").cookie(laptop)).andReturn();
		assertThat(logout.getResponse().getStatus()).isEqualTo(204);
		assertThat(setCookie(logout)).contains("Max-Age=0");

		assertThat(refresh(laptop).getResponse().getStatus()).isEqualTo(401);
		assertThat(refresh(phone).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void logoutWithoutCookieStillSucceeds() throws Exception {
		mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
	}

	@Test
	void meNeedsAnAccessToken() throws Exception {
		mvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

}
