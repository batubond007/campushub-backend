package com.campushub.controller;

import static com.campushub.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** COD-129 / US-24: PUT and DELETE /api/events/{id} (University Admins only). */
@SpringBootTest
@AutoConfigureMockMvc
class EventEditDeleteTest {

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

	private Event event(String title, int startsInHours, int endsInHours) {
		Instant now = Instant.now();
		return events.save(new Event(title, "About " + title, "Library", now.plus(startsInHours, ChronoUnit.HOURS),
				now.plus(endsInHours, ChronoUnit.HOURS), "ODTÜ Library", true, admin.getId(), now));
	}

	private static String body(String title, Instant start, Instant end) {
		return """
				{"title": "%s", "description": " New description ", "location": " Hall B ",
				 "startTime": "%s", "endTime": "%s"}""".formatted(title, start, end);
	}

	private ResultActions edit(Object id, String body, String token) throws Exception {
		return mvc.perform(put("/api/events/" + id).header("Authorization", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions remove(Object id, String token) throws Exception {
		return mvc.perform(delete("/api/events/" + id).header("Authorization", token));
	}

	private static Instant inDays(int days) {
		return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
	}

	@Test
	void adminEditsAnEventAndTheChangeIsSaved() throws Exception {
		Event event = event("Workshop", 24, 26);
		Instant start = inDays(3);

		edit(event.getId(), body(" Renamed Workshop ", start, start.plus(2, ChronoUnit.HOURS)), adminToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(event.getId()))
			.andExpect(jsonPath("$.title").value("Renamed Workshop"))
			.andExpect(jsonPath("$.description").value("New description"))
			.andExpect(jsonPath("$.location").value("Hall B"))
			.andExpect(jsonPath("$.startTime").value(start.toString()))
			// The organizer stays the original one.
			.andExpect(jsonPath("$.organizerName").value("ODTÜ Library"))
			.andExpect(jsonPath("$.ended").value(false));

		mvc.perform(get("/api/events/" + event.getId()).header("Authorization", studentToken))
			.andExpect(jsonPath("$.title").value("Renamed Workshop"));
	}

	@Test
	void anEndedEventCannotBeEdited() throws Exception {
		Event event = event("Orientation Day", -10, -2);
		Instant start = inDays(1);
		edit(event.getId(), body("Again", start, start.plus(1, ChronoUnit.HOURS)), adminToken)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EVENT_ENDED"));
		assertThat(events.findById(event.getId()).orElseThrow().getTitle()).isEqualTo("Orientation Day");
	}

	@Test
	void editUsesTheSameFieldRulesAsCreate() throws Exception {
		Event event = event("Workshop", 24, 26);
		edit(event.getId(), "{\"title\": \"\", \"description\": \"x\"}", adminToken).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors.title").value("Title is required."))
			.andExpect(jsonPath("$.fieldErrors.location").value("Location is required."));

		Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
		edit(event.getId(), body("Workshop", past, past.minus(1, ChronoUnit.HOURS)), adminToken)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.startTime").value("Start time must be in the future."))
			.andExpect(jsonPath("$.fieldErrors.endTime").value("End time must be after the start time."));
	}

	@Test
	void adminDeletesAnEventAndItIsGone() throws Exception {
		Event event = event("Workshop", 24, 26);
		remove(event.getId(), adminToken).andExpect(status().isNoContent());

		assertThat(events.findById(event.getId())).isEmpty();
		mvc.perform(get("/api/events/" + event.getId()).header("Authorization", studentToken))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/events").header("Authorization", studentToken))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void unknownOrNonNumericIdIsNotFound() throws Exception {
		Instant start = inDays(1);
		for (String id : new String[] { "999999", "abc" }) {
			edit(id, body("X", start, start.plus(1, ChronoUnit.HOURS)), adminToken).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
			remove(id, adminToken).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		}
	}

	@Test
	void otherRolesCannotEditOrDelete() throws Exception {
		Event event = event("Workshop", 24, 26);
		Instant start = inDays(1);
		edit(event.getId(), body("Hacked", start, start.plus(1, ChronoUnit.HOURS)), studentToken)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		// The role is checked first: even an invalid body gets 403, not 400.
		edit(event.getId(), "{}", studentToken).andExpect(status().isForbidden());
		remove(event.getId(), studentToken).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		assertThat(events.findById(event.getId()).orElseThrow().getTitle()).isEqualTo("Workshop");
	}

	@Test
	void needsSigningIn() throws Exception {
		mvc.perform(delete("/api/events/1")).andExpect(status().isUnauthorized());
		mvc.perform(put("/api/events/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

}
