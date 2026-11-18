package com.campushub.demo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.campushub.model.Event;

/**
 * The frontend mock's events (mock/eventsMock.ts), placed a few days from today at sensible ODTÜ times:
 * 19 upcoming ones (including a 3-day festival and a 24-hour hackathon) and one that has already ended.
 */
final class DemoEvents {

	private static final ZoneId CAMPUS = ZoneId.of("Europe/Istanbul");

	private DemoEvents() {
	}

	private record Seed(String title, String description, String location, int day, double from, int endDay,
			double to, String organizer) {
	}

	static List<Event> create(Long createdBy, Clock clock) {
		LocalDate today = LocalDate.now(clock.withZone(CAMPUS));
		Instant now = clock.instant();
		List<Event> events = new ArrayList<>();
		for (Seed s : seeds()) {
			events.add(new Event(s.title(), s.description(), s.location(), at(today, s.day(), s.from()),
					at(today, s.endDay(), s.to()), s.organizer(), true, createdBy, now));
		}
		return events;
	}

	/** {@code hours} may have a fraction: 15.5 is 15:30 campus time. */
	private static Instant at(LocalDate today, int days, double hours) {
		ZonedDateTime midnight = today.plusDays(days).atStartOfDay(CAMPUS);
		return midnight.plusMinutes(Math.round(hours * 60)).toInstant();
	}

	private static List<Seed> seeds() {
		List<Seed> seeds = new ArrayList<>(List.of(
				new Seed("Welcome Week Campus Tour",
						"Meet other new students and discover the main campus buildings with student guides.\n\n"
								+ "The tour visits the Library, the Cafeteria, the Sports Centre and the faculty buildings, and ends at the "
								+ "Student Centre with tea and simit. Comfortable shoes recommended: the walk takes about two hours.",
						"Main Gate (A1)", 2, 10, 2, 12, "ODTÜ Student Affairs"),
				new Seed("Spring Career Fair",
						"More than 60 companies meet ODTÜ students for internships and graduate positions.\n\n"
								+ "Bring printed copies of your CV. The Career Centre will review CVs at its stand between 10:00 and 12:00, "
								+ "and short company talks run in Hall B every hour.",
						"Kültür ve Kongre Merkezi", 6, 10, 6, 17, "ODTÜ Career Centre"),
				new Seed("Library Research Skills Workshop",
						"Learn how to find articles, use the library databases and manage your references for term projects.\n\n"
								+ "Bring your laptop. Places are limited to the seminar room capacity.",
						"Library, Seminar Room 2", 9, 14, 9, 15.5, "ODTÜ Library"),
				new Seed("Spring Concert: ODTÜ Symphony Orchestra",
						"The ODTÜ Symphony Orchestra plays Dvořák’s “New World” Symphony and works by Turkish composers.\n\n"
								+ "Free entry for students with a student ID. Doors open 30 minutes before the concert.",
						"Kültür ve Kongre Merkezi, Main Hall", 4, 19.5, 4, 21.5, "ODTÜ Culture Office"),
				new Seed("Mental Health Awareness Talk",
						"Counsellors from the Medical Centre talk about exam stress, sleep and where to get support on campus.\n\n"
								+ "The session is in Turkish with English slides. Questions can be asked anonymously.",
						"Online (link shared with registered students)", 12, 17, 12, 18, "ODTÜ Medical Centre"),
				new Seed("ODTÜ Spring Festival",
						"Three days of concerts, club stands and food trucks on the Devrim Stadium grounds.\n\n"
								+ "Concerts run until late every night; the festival area opens at 12:00 each day.",
						"Devrim Stadium", 15, 12, 17, 23.5, "ODTÜ Culture Office"),
				new Seed("Hackathon: Campus Apps",
						"A 24-hour hackathon: build an app that makes campus life easier. Teams of 2–4, mentors on site all night.\n\n"
								+ "Food and drinks are provided. Bring your laptop and charger.",
						"Computer Engineering Building, A101", 20, 18, 21, 18, "ODTÜ Computer Engineering"),
				new Seed("Orientation Day (past)",
						"This event has already ended, so it does not appear in the upcoming list.",
						"Kültür ve Kongre Merkezi", -3, 10, -3, 16, "ODTÜ Student Affairs")));

		Object[][] short_ = {
				{ "Chess Tournament", "Student Centre, Room 3", 3, 13, 17 },
				{ "Photography Walk", "Eymir Lake", 5, 9, 12 },
				{ "Startup Pitch Night", "Technopolis, Hall 1", 7, 18, 21 },
				{ "Blood Donation Day", "Medical Centre", 8, 10, 16 },
				{ "Debate Club Open Session", "Faculty of Arts, Room 210", 10, 17, 19 },
				{ "Robotics Demo Day", "Mechanical Engineering, Hangar", 11, 13, 17 },
				{ "Turkish Folk Dance Show", "Kültür ve Kongre Merkezi, Main Hall", 13, 19, 21 },
				{ "Climbing Wall Taster", "Sports Centre", 14, 15, 17 },
				{ "Data Science Meetup", "Library, Seminar Room 1", 16, 18, 20 },
				{ "Campus Clean-up", "Main Gate (A1)", 18, 10, 13 },
				{ "Film Night: Classic Turkish Cinema", "Faculty of Architecture, Auditorium", 19, 20, 22.5 },
				{ "Graduate Study Abroad Info Session", "Online (link shared with registered students)", 22, 14, 15 } };
		for (Object[] e : short_) {
			String title = (String) e[0];
			String location = (String) e[1];
			int day = (Integer) e[2];
			seeds.add(new Seed(title, title + " at " + location + ". Open to all ODTÜ students.", location, day,
					((Number) e[3]).doubleValue(), day, ((Number) e[4]).doubleValue(), "ODTÜ Student Affairs"));
		}
		return seeds;
	}

}
