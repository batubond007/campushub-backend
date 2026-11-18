package com.campushub.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * No e-mail yet: the code is written to the server log (and with {@code campushub.otp.fixed-code} it is always
 * the same, e.g. 123456). Replace with a mail-based {@link OtpSender} when e-mail is set up.
 */
@Component
class LoggingOtpSender implements OtpSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

	@Override
	public void send(String email, String code, Duration validFor) {
		log.info("Verification code for {}: {} (valid for {} minutes)", email, code, validFor.toMinutes());
	}

}
