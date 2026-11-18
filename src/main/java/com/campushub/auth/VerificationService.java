package com.campushub.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campushub.auth.otp.OtpService;
import com.campushub.common.error.ApiException;
import com.campushub.common.error.ErrorCode;
import com.campushub.user.AccountStatus;
import com.campushub.user.Emails;
import com.campushub.user.User;
import com.campushub.user.UserRepository;

/** US-02: the student types the e-mailed code; a correct code activates the account. */
@Service
public class VerificationService {

	private final UserRepository users;

	private final OtpService otps;

	public VerificationService(UserRepository users, OtpService otps) {
		this.users = users;
		this.otps = otps;
	}

	/** No rollback on ApiException: a wrong code must still be counted as an attempt. */
	@Transactional(noRollbackFor = ApiException.class)
	public MessageResponse verify(VerifyRequest request) {
		User user = pendingUser(request.email());
		otps.check(user, request.code());
		user.setStatus(AccountStatus.ACTIVE);
		return new MessageResponse("Your e-mail is verified. You can log in now.");
	}

	/** A new code; the previous one stops working. */
	@Transactional
	public MessageResponse resend(ResendCodeRequest request) {
		otps.issue(pendingUser(request.email()));
		return new MessageResponse("We sent a new code.");
	}

	private User pendingUser(String rawEmail) {
		User user = users.findByEmail(Emails.normalize(rawEmail))
			.orElseThrow(() -> new ApiException(ErrorCode.SIGNUP_NOT_FOUND, "There is no sign-up waiting for this e-mail."));
		if (user.getStatus() != AccountStatus.PENDING_VERIFICATION) {
			throw new ApiException(ErrorCode.ALREADY_VERIFIED, "This account is already verified. Please log in.");
		}
		return user;
	}

}
