package com.campushub.event;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

	/** Events that have not ended yet; the sort comes with the Pageable. */
	Page<Event> findByEndTimeAfter(Instant now, Pageable pageable);

}
