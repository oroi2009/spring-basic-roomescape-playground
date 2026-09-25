package roomescape.slot;

import jakarta.persistence.LockModeType;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roomescape.global.exception.ConstraintViolationUtils;
import roomescape.reservation.exception.ReservationErrorCode;
import roomescape.reservation.exception.ReservationException;

import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Slot s
            where s.date = :date and s.time.id = :timeId and s.theme.id = :themeId
            """)
    Optional<Slot> findForUpdateByDateAndTimeIdAndThemeId(
            @Param("date") String date,
            @Param("timeId") Long timeId,
            @Param("themeId") Long themeId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.id = :slotId")
    Optional<Slot> findForUpdateById(@Param("slotId") Long slotId);

    default Slot saveSlot(Slot slot) {
        try {
            return saveAndFlush(slot);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateSlotViolation(exception)) {
                LoggerFactory.getLogger(SlotRepository.class)
                        .info("Duplicate slot constraint violation: uk_slots_date_time_theme", exception);
                throw new ReservationException(ReservationErrorCode.DUPLICATE_RESERVATION);
            }

            throw exception;
        }
    }

    private static boolean isDuplicateSlotViolation(DataIntegrityViolationException exception) {
        return ConstraintViolationUtils.hasConstraint(exception, "uk_slots_date_time_theme");
    }
}
