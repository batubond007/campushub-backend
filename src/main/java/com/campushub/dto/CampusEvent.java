package com.campushub.dto;

import java.time.Instant;

import com.campushub.model.Event;

/** An event in the list ({@code CampusEvent} in the frontend's {@code src/features/events/types.ts}). */
public record CampusEvent(Long id, String title, String description, String location, Instant startTime,
		Instant endTime, String organizerName, boolean official, Instant createdAt) {

	public static CampusEvent of(Event event) {
		return new CampusEvent(event.getId(), event.getTitle(), event.getDescription(), event.getLocation(),
				event.getStartTime(), event.getEndTime(), event.getOrganizerName(), event.isOfficial(),
				event.getCreatedAt());
	}

}
