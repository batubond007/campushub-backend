package com.campushub.service;

import java.time.Duration;

/** Delivers a verification code to the student. Today: {@link LoggingOtpSender}; an SMTP sender can replace it later. */
public interface OtpSender {

	void send(String email, String code, Duration validFor);

}
