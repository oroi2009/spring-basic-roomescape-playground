package roomescape.slot;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
