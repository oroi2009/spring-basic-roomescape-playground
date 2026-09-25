package roomescape.reservation;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roomescape.global.exception.ConstraintViolationUtils;
import roomescape.reservation.exception.ReservationErrorCode;
import roomescape.reservation.exception.ReservationException;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Override
    @Query("select r from Reservation r join fetch r.slot s join fetch s.time join fetch s.theme")
    List<Reservation> findAll();

    @Query("""
            select r from Reservation r join fetch r.slot s join fetch s.time join fetch s.theme
            where s.date = :date and s.theme.id = :themeId
            """)
    List<Reservation> findByDateAndThemeId(@Param("date") String date, @Param("themeId") Long themeId);

    boolean existsBySlotId(Long slotId);

    boolean existsBySlotIdAndMemberId(Long slotId, Long memberId);

    @Modifying
    @Query("delete from Reservation r where r.id = :reservationId")
    int deleteByReservationId(@Param("reservationId") Long reservationId);

    @Query("""
            select r from Reservation r join fetch r.slot s join fetch s.time join fetch s.theme
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
        return ConstraintViolationUtils.hasConstraint(exception, "uk_reservations_slot");
    }
}
