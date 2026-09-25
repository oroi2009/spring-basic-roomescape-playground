package roomescape.global.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

public final class ConstraintViolationUtils {

    private ConstraintViolationUtils() {
    }

    public static boolean hasConstraint(
            DataIntegrityViolationException exception,
            String constraintName
    ) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violationException
                    && containsConstraint(violationException.getConstraintName(), constraintName)) {
                return true;
            }
            cause = cause.getCause();
        }

        Throwable mostSpecificCause = exception.getMostSpecificCause();
        return mostSpecificCause != null
                && containsConstraint(mostSpecificCause.getMessage(), constraintName);
    }

    private static boolean containsConstraint(String value, String constraintName) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(constraintName.toLowerCase(Locale.ROOT));
    }
}
