package com.campushub.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * The sign-up password rule (US-01), the same as the frontend's {@code passwordChecks}: at least 8 characters
 * with a letter and a number. At most 72 <em>bytes</em> in UTF-8, because BCrypt ignores everything after that;
 * Turkish letters such as ş or ğ take 2 bytes each.
 */
@Documented
@Constraint(validatedBy = StrongPassword.Validator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

	String message() default "Password must be at least 8 characters with a letter and a number.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	int MAX_BYTES = 72;

	class Validator implements ConstraintValidator<StrongPassword, String> {

		@Override
		public boolean isValid(String password, ConstraintValidatorContext context) {
			if (password == null) {
				return false;
			}
			if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
				context.disableDefaultConstraintViolation();
				context.buildConstraintViolationWithTemplate("Password is too long. Please use a shorter one.")
					.addConstraintViolation();
				return false;
			}
			return password.length() >= 8 && password.chars().anyMatch(Character::isLetter)
					&& password.chars().anyMatch(Character::isDigit);
		}

	}

}
