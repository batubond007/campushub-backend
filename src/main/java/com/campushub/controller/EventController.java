package com.campushub.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.campushub.dto.CampusEvent;
import com.campushub.dto.CreateEventRequest;
import com.campushub.dto.EventDetail;
import com.campushub.dto.PageResponse;
import com.campushub.security.CurrentUser;
import com.campushub.service.EventService;

/**
 * {@code /api/events}: every signed-in user can list events and open one; only University Admins create, edit and
 * delete them
 * (frontend README "Roles").
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

	static final int MAX_PAGE_SIZE = 50;

	private final EventService events;

	public EventController(EventService events) {
		this.events = events;
	}

	/**
	 * Paging like Spring's Pageable: {@code page} from 0, {@code size} 1–50 (defaults 0 and 10).
	 * Invalid values -> 400 VALIDATION_FAILED with fieldErrors.page / fieldErrors.size.
	 */
	@GetMapping
	public PageResponse<CampusEvent> list(
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be a whole number, 0 or more.") int page,
			@RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be a whole number from 1 to 50.")
			@Max(value = MAX_PAGE_SIZE, message = "size must be a whole number from 1 to 50.") int size) {
		return events.upcoming(page, size);
	}

	/**
	 * US-06: everything about one event. 404 NOT_FOUND when it does not exist; a non-numeric id
	 * ({@code /api/events/abc}) cannot exist either, so it is a 404 too, not a 400.
	 */
	@GetMapping("/{id}")
	public EventDetail detail(@PathVariable String id) {
		return events.detail(eventId(id));
	}

	/** University Admins only (403 FORBIDDEN otherwise). Errors: VALIDATION_FAILED with fieldErrors. */
	@PostMapping
	public CampusEvent create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateEventRequest request) {
		return events.create(CurrentUser.id(jwt), request);
	}

	/**
	 * COD-129 / US-24: University Admins only (403 FORBIDDEN otherwise). Same body and field rules as create.
	 * Errors: 404 NOT_FOUND, 409 EVENT_ENDED when the event is already over, VALIDATION_FAILED with fieldErrors.
	 */
	@PutMapping("/{id}")
	public EventDetail update(@PathVariable String id, @Valid @RequestBody CreateEventRequest request) {
		return events.update(eventId(id), request);
	}

	/** COD-129 / US-24: University Admins only (403 FORBIDDEN otherwise). 204 No Content; 404 NOT_FOUND. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String id) {
		events.delete(eventId(id));
	}

	/** A non-numeric id ({@code /api/events/abc}) cannot exist, so it is a 404, not a 400. */
	private static long eventId(String id) {
		try {
			return Long.parseLong(id);
		}
		catch (NumberFormatException ex) {
			throw EventService.notFound();
		}
	}

}
