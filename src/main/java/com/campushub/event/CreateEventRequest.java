package com.campushub.event;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/events} body ({@code CreateEventRequest} in the frontend). Times are ISO-8601 instants,
 * e.g. {@code 2026-12-01T19:00:00.000Z}; an event may end on a later day (past midnight, several days).
 */
public record CreateEventRequest(
		@NotBlank(message = "Title is required.")
		@Size(max = 120, message = "Title can be at most 120 characters.") String title,
		@NotBlank(message = "Description is required.")
		@Size(max = 2000, message = "Description can be at most 2000 characters.") String description,
		@NotBlank(message = "Location is required.")
		@Size(max = 200, message = "Location can be at most 200 characters.") String location,
		@NotNull(message = "Start time is required.") Instant startTime,
		@NotNull(message = "End time is required.") Instant endTime) {
}
