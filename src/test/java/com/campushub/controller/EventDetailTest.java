package com.campushub.controller;

import static com.campushub.support.TestTokens.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.campushub.model.AccountStatus;
import com.campushub.model.Event;
import com.campushub.model.Role;
import com.campushub.model.User;
import com.campushub.repository.EventRepository;
import com.campushub.repository.UserRepository;
import com.campushub.service.AccessTokenService;

/** COD-14 / US-06: GET /api/events/{id}. */
@SpringBootTest
@AutoConfigureMockMvc
class EventDetailTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private EventRepository events;

	@Autowired
	private UserRepository users;

	@Autowired
	private AccessTokenService tokens;

	private User user;

	private String token;

	@BeforeEach
	void setUp() {
		user = users.findByEmail("reader@metu.edu.tr")
			.orElseGet(() -> users.save(new User("reader@metu.edu.tr", "x", "Reader", Role.STUDENT,
					AccountStatus.ACTIVE, Instant.now())));
		token = bearer(tokens.issue(user).value());
	}

	private Event event(String title, int startsInHours, int endsInHours) {
		Instant now = Instant.now();
		return events.save(new Event(title, "First paragraph.\n\nSecond paragraph.", "Library",
				now.plus(startsInHours, ChronoUnit.HOURS), now.plus(endsInHours, ChronoUnit.HOURS), "ODTÜ Library",
				true, user.getId(), now));
	}

	@Test
	void showsEverythingAboutAnUpcomingEvent() throws Exception {
		Event event = event("Workshop", 24, 26);
		mvc.perform(get("/api/events/" + event.getId()).header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(event.getId()))
			.andExpect(jsonPath("$.title").value("Workshop"))
			.andExpect(jsonPath("$.description").value("First paragraph.\n\nSecond paragraph."))
			.andExpect(jsonPath("$.location").value("Library"))
			.andExpect(jsonPath("$.organizerName").value("ODTÜ Library"))
			.andExpect(jsonPath("$.official").value(true))
			.andExpect(jsonPath("$.ended").value(false));
	}

	@Test
	void anEndedEventStillOpensAndSaysSo() throws Exception {
		Event event = event("Orientation Day", -10, -2);
		mvc.perform(get("/api/events/" + event.getId()).header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ended").value(true));
	}

	@Test
	void unknownOrNonNumericIdIsNotFound() throws Exception {
		for (String id : new String[] { "999999", "abc" }) {
			mvc.perform(get("/api/events/" + id).header("Authorization", token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("This event could not be found. It may have been removed."));
		}
	}

	@Test
	void needsSigningIn() throws Exception {
		mvc.perform(get("/api/events/1")).andExpect(status().isUnauthorized());
	}

}
