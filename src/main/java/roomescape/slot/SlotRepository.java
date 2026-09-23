package roomescape.slot;

import jakarta.persistence.LockModeType;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roomescape.slot.exception.SlotErrorCode;
import roomescape.slot.exception.SlotException;

import java.util.Locale;
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
                throw new SlotException(SlotErrorCode.DUPLICATE_SLOT);
            }

            throw exception;
        }
    }

    private static boolean isDuplicateSlotViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException) {
                if (containsDuplicateSlotConstraint(constraintViolationException.getConstraintName())) {
                    return true;
                }
            }
            cause = cause.getCause();
        }

        return containsDuplicateSlotConstraint(exception.getMostSpecificCause().getMessage());
    }

    private static boolean containsDuplicateSlotConstraint(String value) {
        return value != null && value.toLowerCase(Locale.ROOT)
                .contains("uk_slots_date_time_theme");
    }
}
