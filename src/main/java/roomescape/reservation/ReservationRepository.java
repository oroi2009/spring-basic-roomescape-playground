package roomescape.reservation;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roomescape.reservation.exception.ReservationErrorCode;
import roomescape.reservation.exception.ReservationException;

import java.util.List;
import java.util.Locale;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Override
    @Query("select r from Reservation r join fetch r.time join fetch r.theme")
    List<Reservation> findAll();

    List<Reservation> findByDateAndThemeId(String date, Long themeId);

    boolean existsByDateAndTimeIdAndThemeId(String date, Long timeId, Long themeId);

    boolean existsByDateAndTimeIdAndThemeIdAndMemberId(String date, Long timeId, Long themeId, Long memberId);

    @Query("""
            select r from Reservation r join fetch r.time join fetch r.theme
            where r.member.id = :memberId
            order by r.id
            """)
    List<Reservation> findAllByMemberId(@Param("memberId") Long memberId);

    default Reservation saveReservation(Reservation reservation) {
        try {
            return saveAndFlush(reservation);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateReservationViolation(exception)) {
                throw new ReservationException(ReservationErrorCode.DUPLICATE_RESERVATION);
            }

            throw exception;
        }
    }

    private static boolean isDuplicateReservationViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException) {
                if (containsDuplicateReservationConstraint(constraintViolationException.getConstraintName())) {
                    return true;
                }
            }
            cause = cause.getCause();
        }

        return containsDuplicateReservationConstraint(exception.getMostSpecificCause().getMessage());
    }

    private static boolean containsDuplicateReservationConstraint(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("uk_reservations_date_time_theme");
    }
}
