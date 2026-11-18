package com.campushub.event;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campushub.common.PageResponse;
import com.campushub.common.error.ApiException;
import com.campushub.common.error.ErrorCode;
import com.campushub.common.error.Problems;
import com.campushub.user.User;
import com.campushub.user.UserRepository;

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

	static ApiException notFound() {
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

		User organizer = users.findById(userId).orElseThrow(Problems::unauthenticated);
		Event event = events.save(new Event(request.title().trim(), request.description().trim(),
				request.location().trim(), request.startTime(), request.endTime(), organizer.getFullName(), true,
				organizer.getId(), now));
		return CampusEvent.of(event);
	}

}
