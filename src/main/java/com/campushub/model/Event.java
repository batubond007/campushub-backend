package com.campushub.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A campus event (table {@code events}, see V4__events.sql). */
@Entity
@Table(name = "events")
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(nullable = false, length = 2000)
	private String description;

	@Column(nullable = false, length = 200)
	private String location;

	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Column(name = "end_time", nullable = false)
	private Instant endTime;

	@Column(name = "organizer_name", nullable = false, length = 100)
	private String organizerName;

	@Column(nullable = false)
	private boolean official;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Event() {
	}

	public Event(String title, String description, String location, Instant startTime, Instant endTime,
			String organizerName, boolean official, Long createdBy, Instant createdAt) {
		this.title = title;
		this.description = description;
		this.location = location;
		this.startTime = startTime;
		this.endTime = endTime;
		this.organizerName = organizerName;
		this.official = official;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getLocation() {
		return location;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public Instant getEndTime() {
		return endTime;
	}

	public String getOrganizerName() {
		return organizerName;
	}

	public boolean isOfficial() {
		return official;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
