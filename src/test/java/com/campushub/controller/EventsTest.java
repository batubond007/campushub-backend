package com.campushub.controller;

import static com.campushub.support.TestTokens.bearer;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.campushub.model.AccountStatus;
import com.campushub.model.Event;
import com.campushub.model.Role;
import com.campushub.model.User;
import com.campushub.repository.EventRepository;
import com.campushub.repository.UserRepository;
import com.campushub.service.AccessTokenService;

/** COD-118: GET /api/events (paging) and POST /api/events (University Admins only). */
@SpringBootTest
@AutoConfigureMockMvc
class EventsTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private EventRepository events;

	@Autowired
	private UserRepository users;

	@Autowired
	private AccessTokenService tokens;

	private User admin;

	private String adminToken;

	private String studentToken;

	@BeforeEach
	void setUp() {
		events.deleteAll();
		admin = users.findByEmail("admin@metu.edu.tr")
			.orElseGet(() -> users.save(new User("admin@metu.edu.tr", "x", "Zeynep Demir", Role.UNIVERSITY_ADMIN,
					AccountStatus.ACTIVE, Instant.now())));
		User student = users.findByEmail("student@metu.edu.tr")
			.orElseGet(() -> users.save(new User("student@metu.edu.tr", "x", "Ayşe Yılmaz", Role.STUDENT,
					AccountStatus.ACTIVE, Instant.now())));
		adminToken = bearer(tokens.issue(admin).value());
		studentToken = bearer(tokens.issue(student).value());
	}

	private void event(String title, int startsInHours, int endsInHours) {
		Instant now = Instant.now();
		events.save(new Event(title, "About " + title, "Library", now.plus(startsInHours, ChronoUnit.HOURS),
				now.plus(endsInHours, ChronoUnit.HOURS), "ODTÜ Library", true, admin.getId(), now));
	}

	private ResultActions list(String query, String token) throws Exception {
		return mvc.perform(get("/api/events" + query).header("Authorization", token));
	}

	private ResultActions create(String body, String token) throws Exception {
		return mvc.perform(post("/api/events").header("Authorization", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private static String body(Instant start, Instant end) {
		return """
				{"title": " Overnight Concert ", "description": "Music", "location": "Devrim Stadium",
				 "startTime": "%s", "endTime": "%s"}""".formatted(start, end);
	}

	@Test
	void listsUpcomingEventsSoonestFirstOnePageAtATime() throws Exception {
		event("Later", 48, 50);
		event("Soon", 2, 3);
		event("Ended", -5, -1);
		event("Now on", -1, 1);
		event("Next week", 168, 170);

		list("", studentToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(4)))
			.andExpect(jsonPath("$.content[0].title").value("Now on"))
			.andExpect(jsonPath("$.content[1].title").value("Soon"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalElements").value(4))
			.andExpect(jsonPath("$.totalPages").value(1));

		list("?page=1&size=2", studentToken)
			.andExpect(jsonPath("$.content", hasSize(2)))
			.andExpect(jsonPath("$.content[0].title").value("Later"))
			.andExpect(jsonPath("$.content[0].organizerName").value("ODTÜ Library"))
			.andExpect(jsonPath("$.content[0].official").value(true))
			// Times are ISO-8601 UTC strings, as the frontend expects.
			.andExpect(jsonPath("$.content[0].startTime").value(org.hamcrest.Matchers.endsWith("Z")))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.totalPages").value(2));

		list("?page=9&size=2", studentToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(0)))
			.andExpect(jsonPath("$.totalElements").value(4));
	}

	@Test
	void invalidPagingIsAFieldError() throws Exception {
		list("?size=0", studentToken).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors.size").value("size must be a whole number from 1 to 50."));
		list("?size=51", studentToken).andExpect(jsonPath("$.fieldErrors.size").exists());
		list("?page=-1", studentToken).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.page").value("page must be a whole number, 0 or more."));
		list("?page=abc", studentToken).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void listNeedsSigningIn() throws Exception {
		mvc.perform(get("/api/events")).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void adminCreatesAnEventThatEndsTheNextDay() throws Exception {
		Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
		create(body(start, start.plus(6, ChronoUnit.HOURS)), adminToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.title").value("Overnight Concert"))
			.andExpect(jsonPath("$.organizerName").value("Zeynep Demir"))
			.andExpect(jsonPath("$.official").value(true))
			.andExpect(jsonPath("$.startTime").value(start.toString()));

		list("", studentToken).andExpect(jsonPath("$.content[0].title").value("Overnight Concert"));
	}

	@Test
	void studentsCannotCreateEvents() throws Exception {
		Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
		create(body(start, start.plus(1, ChronoUnit.HOURS)), studentToken).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		// The role is checked first: even an invalid body gets 403, not 400.
		create("{}", studentToken).andExpect(status().isForbidden());
	}

	@Test
	void createValidatesFieldsAndTimes() throws Exception {
		create("{\"title\": \"\", \"description\": \"x\"}", adminToken).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors.title").value("Title is required."))
			.andExpect(jsonPath("$.fieldErrors.location").value("Location is required."))
			.andExpect(jsonPath("$.fieldErrors.startTime").value("Start time is required."));

		Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
		create(body(past, past.minus(1, ChronoUnit.HOURS)), adminToken).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.startTime").value("Start time must be in the future."))
			.andExpect(jsonPath("$.fieldErrors.endTime").value("End time must be after the start time."));

		String tooLong = "{\"title\": \"%s\", \"description\": \"x\", \"location\": \"y\", \"startTime\": \"%s\", \"endTime\": \"%s\"}"
			.formatted("t".repeat(121), Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(2, ChronoUnit.DAYS));
		create(tooLong, adminToken).andExpect(jsonPath("$.fieldErrors.title").value("Title can be at most 120 characters."));
	}

}
