package com.campushub.dto;

import java.time.Instant;

import com.campushub.model.Event;

/**
 * One event with everything the detail page shows ({@code EventDetail} in the frontend: {@code CampusEvent}
 * plus {@code ended}).
 */
public record EventDetail(Long id, String title, String description, String location, Instant startTime,
		Instant endTime, String organizerName, boolean official, Instant createdAt, boolean ended) {

	public static EventDetail of(Event event, Instant now) {
		return new EventDetail(event.getId(), event.getTitle(), event.getDescription(), event.getLocation(),
				event.getStartTime(), event.getEndTime(), event.getOrganizerName(), event.isOfficial(),
				event.getCreatedAt(), !now.isBefore(event.getEndTime()));
	}

}
