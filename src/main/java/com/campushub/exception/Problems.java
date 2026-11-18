package com.campushub.exception;

/** The common errors, so every endpoint uses the same wording (the texts match the frontend mock). */
public final class Problems {

	private Problems() {
	}

	public static ApiException unauthenticated() {
		return new ApiException(ErrorCode.UNAUTHENTICATED, "Your session has ended. Please log in again.");
	}

	public static ApiException forbidden() {
		return new ApiException(ErrorCode.FORBIDDEN, "You do not have permission to do this.");
	}

	public static ApiException notFound(String detail) {
		return new ApiException(ErrorCode.NOT_FOUND, detail);
	}

}
