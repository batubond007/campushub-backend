package com.campushub.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Used while e-mail is off ({@code campushub.mail.enabled=false}, the default): the code is written to the server
 * log (and with {@code campushub.otp.fixed-code} it is always the same, e.g. 123456). See {@link MailOtpSender}.
 */
@Component
@ConditionalOnProperty(name = "campushub.mail.enabled", havingValue = "false", matchIfMissing = true)
class LoggingOtpSender implements OtpSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

	@Override
	public void send(String email, String code, Duration validFor) {
		log.info("Verification code for {}: {} (valid for {} minutes)", email, code, validFor.toMinutes());
	}

}
