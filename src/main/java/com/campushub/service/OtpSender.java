package com.campushub.service;

import java.time.Duration;

/** Delivers a verification code to the student. {@link MailOtpSender} when e-mail is on, otherwise {@link LoggingOtpSender}. */
public interface OtpSender {

	void send(String email, String code, Duration validFor);

}
