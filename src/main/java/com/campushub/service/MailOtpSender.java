package com.campushub.service;

import java.io.UnsupportedEncodingException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.campushub.config.CampushubProperties;
import com.campushub.exception.ApiException;
import com.campushub.exception.ErrorCode;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends the verification code by e-mail over SMTP (Brevo, see {@code spring.mail.*}). Active when
 * {@code campushub.mail.enabled=true}.
 * <p>
 * If sending fails the request fails too, so {@link OtpService#issue} rolls back and the student can simply try
 * again (no account or code is left behind that they never received).
 */
@Component
@ConditionalOnProperty(name = "campushub.mail.enabled", havingValue = "true")
class MailOtpSender implements OtpSender {

	private static final Logger log = LoggerFactory.getLogger(MailOtpSender.class);

	private final JavaMailSender mailSender;

	private final CampushubProperties.Mail settings;

	MailOtpSender(JavaMailSender mailSender, CampushubProperties properties) {
		this.mailSender = mailSender;
		this.settings = properties.mail();
		if (settings.from() == null || settings.from().isBlank()) {
			throw new IllegalStateException(
					"campushub.mail.enabled=true needs campushub.mail.from (CAMPUSHUB_MAIL_FROM), a sender verified in Brevo");
		}
	}

	@Override
	public void send(String email, String code, Duration validFor) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setFrom(settings.from(), settings.fromName());
			helper.setTo(email);
			helper.setSubject("Your CampusHub verification code: " + code);
			helper.setText("""
					Hi,

					your CampusHub verification code is:

					    %s

					It is valid for %d minutes. If you did not sign up for CampusHub, you can ignore this e-mail.

					CampusHub
					""".formatted(code, validFor.toMinutes()));
			mailSender.send(message);
			log.info("Verification code e-mailed to {}", email);
		}
		catch (MailException | MessagingException | UnsupportedEncodingException ex) {
			log.error("Could not e-mail the verification code to {}", email, ex);
			throw new ApiException(ErrorCode.INTERNAL_ERROR,
					"We could not send the verification e-mail. Please try again in a moment.");
		}
	}

}
