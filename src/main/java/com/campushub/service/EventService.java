package com.campushub.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campushub.dto.CampusEvent;
import com.campushub.dto.CreateEventRequest;
import com.campushub.dto.EventDetail;
import com.campushub.dto.PageResponse;
import com.campushub.exception.ApiException;
import com.campushub.exception.ErrorCode;
import com.campushub.exception.Problems;
import com.campushub.model.Event;
import com.campushub.model.User;
import com.campushub.repository.EventRepository;
import com.campushub.repository.UserRepository;

@Service
public class EventService {

	private static final Sort SOONEST_FIRST = Sort.by("startTime").ascending().and(Sort.by("id").ascending());

	private final EventRepository events;

	private final UserRepository users;

	private final Clock clock;

	public EventService(EventRepository events, UserRepository users, Clock clock) {
		this.events = events;
		this.users = users;
		this.clock = clock;
	}

	/** Upcoming events (not ended yet), soonest first. A page past the end has no content (not an error). */
	@Transactional(readOnly = true)
	public PageResponse<CampusEvent> upcoming(int page, int size) {
		var result = events.findByEndTimeAfter(clock.instant(), PageRequest.of(page, size, SOONEST_FIRST));
		return PageResponse.of(result, CampusEvent::of);
	}

	/**
	 * US-06: one event, also after it has ended ({@code ended = true}), so old links still open.
	 *
	 * @throws ApiException NOT_FOUND when there is no such event
	 */
	@Transactional(readOnly = true)
	public EventDetail detail(long id) {
		return events.findById(id)
			.map((event) -> EventDetail.of(event, clock.instant()))
			.orElseThrow(EventService::notFound);
	}

	public static ApiException notFound() {
		return Problems.notFound("This event could not be found. It may have been removed.");
	}

	/**
	 * COD-118: a University Admin creates an official event. The role is also checked per URL in SecurityConfig,
	 * so other roles get 403 before their body is even validated (same order as the frontend mock).
	 */
	@Transactional
	@PreAuthorize("hasRole('UNIVERSITY_ADMIN')")
	public CampusEvent create(long userId, CreateEventRequest request) {
		Instant now = clock.instant();
		checkTimes(request, now);

		User organizer = users.findById(userId).orElseThrow(Problems::unauthenticated);
		Event event = events.save(new Event(request.title().trim(), request.description().trim(),
				request.location().trim(), request.startTime(), request.endTime(), organizer.getFullName(), true,
				organizer.getId(), now));
		return CampusEvent.of(event);
	}

	/**
	 * COD-129 / US-24: a University Admin edits an event. All fields are sent again and follow the same rules as
	 * {@link #create}; the organizer stays the original one.
	 *
	 * @throws ApiException NOT_FOUND when there is no such event, EVENT_ENDED (409) when it is already over,
	 * VALIDATION_FAILED for invalid times
	 */
	@Transactional
	@PreAuthorize("hasRole('UNIVERSITY_ADMIN')")
	public EventDetail update(long id, CreateEventRequest request) {
		Instant now = clock.instant();
		Event event = events.findById(id).orElseThrow(EventService::notFound);
		if (event.hasEnded(now)) {
			throw new ApiException(ErrorCode.EVENT_ENDED, "This event has already ended and can no longer be edited.");
		}
		checkTimes(request, now);

		event.update(request.title().trim(), request.description().trim(), request.location().trim(),
				request.startTime(), request.endTime());
		return EventDetail.of(events.save(event), now);
	}

	/**
	 * COD-129 / US-24: a University Admin deletes an event; it no longer appears in the list or opens.
	 *
	 * @throws ApiException NOT_FOUND when there is no such event
	 */
	@Transactional
	@PreAuthorize("hasRole('UNIVERSITY_ADMIN')")
	public void delete(long id) {
		Event event = events.findById(id).orElseThrow(EventService::notFound);
		events.delete(event);
	}

	/** The rules for start and end time, shared by create and edit (the other fields use Bean Validation). */
	private static void checkTimes(CreateEventRequest request, Instant now) {
		Map<String, String> errors = new LinkedHashMap<>();
		if (!request.startTime().isAfter(now)) {
			errors.put("startTime", "Start time must be in the future.");
		}
		if (!request.endTime().isAfter(request.startTime())) {
			errors.put("endTime", "End time must be after the start time.");
		}
		if (!errors.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Please check the highlighted fields.", errors);
		}
	}

}
