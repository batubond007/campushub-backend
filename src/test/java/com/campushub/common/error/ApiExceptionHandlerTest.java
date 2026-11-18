package com.campushub.common.error;

import static com.campushub.support.TestTokens.accessToken;
import static com.campushub.support.TestTokens.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The error contract: every error is a Problem Details body with {@code code} (and {@code fieldErrors}). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiExceptionHandlerTest.ErrorTestController.class)
class ApiExceptionHandlerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtEncoder encoder;

	@Test
	void apiExceptionBecomesProblemDetailWithCode() throws Exception {
		mvc.perform(get("/api/test/conflict").header("Authorization", bearer(accessToken(encoder, 1, "STUDENT"))))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.title").value("Conflict"))
			.andExpect(jsonPath("$.detail").value("An account with this e-mail already exists. Please log in."))
			.andExpect(jsonPath("$.instance").value("/api/test/conflict"))
			.andExpect(jsonPath("$.code").value("EMAIL_TAKEN"))
			.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void apiExceptionKeepsFieldErrors() throws Exception {
		mvc.perform(get("/api/test/domain").header("Authorization", bearer(accessToken(encoder, 1, "STUDENT"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMAIL_DOMAIN_NOT_ALLOWED"))
			.andExpect(jsonPath("$.fieldErrors.email").value("Use your university e-mail."));
	}

	@Test
	void beanValidationBecomesValidationFailedWithFieldErrors() throws Exception {
		mvc.perform(post("/api/test/validate").header("Authorization", bearer(accessToken(encoder, 1, "STUDENT")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.detail").value("Please check the highlighted fields."))
			.andExpect(jsonPath("$.fieldErrors.name").value("Name is required."));
	}

	@Test
	void malformedJsonIsValidationFailed() throws Exception {
		mvc.perform(post("/api/test/validate").header("Authorization", bearer(accessToken(encoder, 1, "STUDENT")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.instance").value("/api/test/validate"));
	}

	@Test
	void unexpectedErrorIs500WithoutDetail() throws Exception {
		mvc.perform(get("/api/test/boom").header("Authorization", bearer(accessToken(encoder, 1, "STUDENT"))))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.detail").doesNotExist());
	}

	@Test
	void missingTokenIs401Unauthenticated() throws Exception {
		mvc.perform(get("/api/test/conflict"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.detail").value("Your session has ended. Please log in again."));
	}

	@Test
	void expiredTokenIs401Unauthenticated() throws Exception {
		String expired = accessToken(encoder, 1, "STUDENT", Instant.now().minus(1, ChronoUnit.MINUTES));
		mvc.perform(get("/api/test/conflict").header("Authorization", bearer(expired)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void wrongRoleIs403Forbidden() throws Exception {
		mvc.perform(get("/api/test/admin").header("Authorization", bearer(accessToken(encoder, 1, "STUDENT"))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void rightRolePasses() throws Exception {
		mvc.perform(get("/api/test/admin").header("Authorization", bearer(accessToken(encoder, 1, "UNIVERSITY_ADMIN"))))
			.andExpect(status().isOk());
	}

	@TestConfiguration
	@RestController
	static class ErrorTestController {

		record NameRequest(@NotBlank(message = "Name is required.") @Size(max = 10) String name) {
		}

		@GetMapping("/api/test/conflict")
		void conflict() {
			throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this e-mail already exists. Please log in.");
		}

		@GetMapping("/api/test/domain")
		void domain() {
			throw new ApiException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED, "Use your university e-mail.",
					Map.of("email", "Use your university e-mail."));
		}

		@PostMapping("/api/test/validate")
		void validate(@Valid @RequestBody NameRequest body) {
		}

		@GetMapping("/api/test/boom")
		void boom() {
			throw new IllegalStateException("database password is hunter2");
		}

		@GetMapping("/api/test/admin")
		@PreAuthorize("hasRole('UNIVERSITY_ADMIN')")
		void admin() {
		}

	}

}
