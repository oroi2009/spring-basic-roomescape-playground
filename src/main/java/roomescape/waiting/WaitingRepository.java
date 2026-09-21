package roomescape.waiting;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roomescape.waiting.exception.WaitingErrorCode;
import roomescape.waiting.exception.WaitingException;

import java.time.LocalDateTime;
import java.util.Locale;

public interface WaitingRepository extends JpaRepository<Waiting, Long> {

    @Query("""
            select count(w) from Waiting w
            where w.date = :date and w.time.id = :timeId and w.theme.id = :themeId
              and (
                  w.createdAt < :createdAt
                  or (w.createdAt = :createdAt and w.id < :waitingId)
              )
            """)
    long countEarlierWaitings(
            @Param("date") String date,
            @Param("timeId") Long timeId,
            @Param("themeId") Long themeId,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("waitingId") Long waitingId
    );

    default Waiting saveWaiting(Waiting waiting) {
        try {
            return saveAndFlush(waiting);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateWaitingViolation(exception)) {
                throw new WaitingException(WaitingErrorCode.DUPLICATE_WAITING);
            }

            throw exception;
        }
    }

    private static boolean isDuplicateWaitingViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException) {
                if (containsDuplicateWaitingConstraint(constraintViolationException.getConstraintName())) {
                    return true;
                }
            }
            cause = cause.getCause();
        }

        return containsDuplicateWaitingConstraint(exception.getMostSpecificCause().getMessage());
    }

    private static boolean containsDuplicateWaitingConstraint(String value) {
        return value != null && value.toLowerCase(Locale.ROOT)
                .contains("uk_waitings_member_date_time_theme");
    }
}
