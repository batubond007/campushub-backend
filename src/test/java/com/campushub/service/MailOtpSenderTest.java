package com.campushub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.campushub.config.CampushubProperties;
import com.campushub.exception.ApiException;
import com.campushub.exception.ErrorCode;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/** The SMTP sender for verification codes (Brevo); the mail server itself is mocked. */
class MailOtpSenderTest {

	private final JavaMailSender mailSender = mock(JavaMailSender.class);

	@BeforeEach
	void setUp() {
		when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
	}

	private static CampushubProperties properties(String from) {
		return new CampushubProperties(null, List.of("metu.edu.tr"), null,
				new CampushubProperties.Mail(true, from, "CampusHub"), null, null);
	}

	@Test
	void sendsTheCodeFromTheConfiguredSender() throws Exception {
		new MailOtpSender(mailSender, properties("noreply@example.com")).send("ali@metu.edu.tr", "654321",
				Duration.ofMinutes(10));

		ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(sent.capture());
		MimeMessage message = sent.getValue();
		InternetAddress from = (InternetAddress) message.getFrom()[0];
		assertThat(from.getAddress()).isEqualTo("noreply@example.com");
		assertThat(from.getPersonal()).isEqualTo("CampusHub");
		assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("ali@metu.edu.tr");
		assertThat(message.getSubject()).contains("654321");
		assertThat((String) message.getContent()).contains("654321").contains("10 minutes");
	}

	@Test
	void aFailedSendBecomesAnApiError() {
		doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));
		MailOtpSender sender = new MailOtpSender(mailSender, properties("noreply@example.com"));

		assertThatThrownBy(() -> sender.send("ali@metu.edu.tr", "654321", Duration.ofMinutes(10)))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INTERNAL_ERROR));
	}

	@Test
	void refusesToStartWithoutASender() {
		assertThatThrownBy(() -> new MailOtpSender(mailSender, properties(" ")))
			.isInstanceOf(IllegalStateException.class);
	}

}
