package com.campushub.user;

import java.util.Locale;

public final class Emails {

	private Emails() {
	}

	/** E-mails are compared and stored trimmed and lower-case, so "Ayse@METU.edu.tr " is the same account. */
	public static String normalize(String email) {
		return (email == null) ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	/** The part after the last {@code @}, e.g. {@code metu.edu.tr}. */
	public static String domain(String normalizedEmail) {
		int at = normalizedEmail.lastIndexOf('@');
		return (at < 0) ? "" : normalizedEmail.substring(at + 1);
	}

}
