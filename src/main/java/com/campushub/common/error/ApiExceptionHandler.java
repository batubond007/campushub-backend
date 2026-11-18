package com.campushub.common.error;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Turns every error into an RFC 9457 Problem Details body (Spring {@link ProblemDetail},
 * {@code application/problem+json}) with two extra members, as agreed with the frontend:
 * <ul>
 * <li>{@code code}: an {@link ErrorCode}, so the frontend never has to read the {@code detail} text</li>
 * <li>{@code fieldErrors}: per-field messages for validation errors, keyed by request field name</li>
 * </ul>
 * Unexpected errors become {@code 500 INTERNAL_ERROR} without a {@code detail}: nothing internal leaks out,
 * the stack trace only goes to the server log.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private static final String VALIDATION_DETAIL = "Please check the highlighted fields.";

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.code().status(), ex.getMessage());
		return respond(problem, ex.code(), ex.fieldErrors(), request.getRequestURI());
	}

	/** {@code @PreAuthorize} refusals are thrown inside the controller, so they arrive here, not at the security filter. */
	@ExceptionHandler({ AccessDeniedException.class, AuthorizationDeniedException.class })
	public ResponseEntity<ProblemDetail> handleAccessDenied(Exception ex, HttpServletRequest request) {
		return handleApiException(Problems.forbidden(), request);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
		return handleApiException(Problems.unauthenticated(), request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		return respond(problem, ErrorCode.INTERNAL_ERROR, Map.of(), request.getRequestURI());
	}

	/** {@code @Valid @RequestBody} failed: one message per field. */
	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return validationFailed(fieldErrors, request);
	}

	/** Constraints on {@code @RequestParam} / {@code @PathVariable} failed (e.g. {@code ?size=0}). */
	@Override
	protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		ex.getParameterValidationResults().forEach((result) -> {
			if (result instanceof ParameterErrors errors) {
				errors.getFieldErrors().forEach((e) -> fieldErrors.putIfAbsent(e.getField(), e.getDefaultMessage()));
			}
			else if (!result.getResolvableErrors().isEmpty()) {
				String name = result.getMethodParameter().getParameterName();
				fieldErrors.putIfAbsent(name, result.getResolvableErrors().getFirst().getDefaultMessage());
			}
		});
		return validationFailed(fieldErrors, request);
	}

	/**
	 * Spring's own errors (malformed JSON, unknown path, wrong method, ...) already come as ProblemDetail;
	 * add our {@code code} where one fits, and the request path as {@code instance}.
	 */
	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		if (body instanceof ProblemDetail problem) {
			ErrorCode code = switch (statusCode.value()) {
				case 400 -> ErrorCode.VALIDATION_FAILED;
				case 401 -> ErrorCode.UNAUTHENTICATED;
				case 403 -> ErrorCode.FORBIDDEN;
				case 404 -> ErrorCode.NOT_FOUND;
				default -> null;
			};
			Map<String, Object> properties = problem.getProperties();
			if (code != null && (properties == null || !properties.containsKey("code"))) {
				problem.setProperty("code", code.name());
			}
			if (problem.getInstance() == null) {
				problem.setInstance(URI.create(path(request)));
			}
		}
		return super.handleExceptionInternal(ex, body, headers, statusCode, request);
	}

	private ResponseEntity<Object> validationFailed(Map<String, String> fieldErrors, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_DETAIL);
		ResponseEntity<ProblemDetail> response = respond(problem, ErrorCode.VALIDATION_FAILED, fieldErrors, path(request));
		return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
	}

	private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem, ErrorCode code,
			Map<String, String> fieldErrors, String path) {
		problem.setInstance(URI.create(path));
		problem.setProperty("code", code.name());
		if (!fieldErrors.isEmpty()) {
			problem.setProperty("fieldErrors", fieldErrors);
		}
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

	private static String path(WebRequest request) {
		return (request instanceof ServletWebRequest servlet) ? servlet.getRequest().getRequestURI() : "";
	}

}
