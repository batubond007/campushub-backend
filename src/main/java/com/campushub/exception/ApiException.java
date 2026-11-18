package com.campushub.exception;

import java.util.Map;

/**
 * Throw this from any service or controller to answer with a Problem Details body:
 * the HTTP status comes from the {@link ErrorCode}, {@code detail} is the text shown to the user.
 *
 * <pre>{@code
 * throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this e-mail already exists. Please log in.");
 * }</pre>
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final Map<String, String> fieldErrors;

	public ApiException(ErrorCode code, String detail) {
		this(code, detail, Map.of());
	}

	/** With per-field messages, keyed by request field name, shown under the matching form field. */
	public ApiException(ErrorCode code, String detail, Map<String, String> fieldErrors) {
		super(detail);
		this.code = code;
		this.fieldErrors = Map.copyOf(fieldErrors);
	}

	public ErrorCode code() {
		return code;
	}

	public Map<String, String> fieldErrors() {
		return fieldErrors;
	}

}
