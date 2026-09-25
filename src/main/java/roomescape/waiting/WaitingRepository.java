package roomescape.waiting;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roomescape.global.exception.ConstraintViolationUtils;
import roomescape.waiting.exception.WaitingErrorCode;
import roomescape.waiting.exception.WaitingException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WaitingRepository extends JpaRepository<Waiting, Long> {

    @Modifying
    @Query("delete from Waiting w where w.id = :waitingId and w.member.id = :memberId")
    int deleteByIdAndMemberId(@Param("waitingId") Long waitingId, @Param("memberId") Long memberId);

    @EntityGraph(attributePaths = "member")
    Optional<Waiting> findFirstBySlotIdOrderByCreatedAtAscIdAsc(Long slotId);

    @Query("""
            select new roomescape.waiting.WaitingWithRank(w,
                (select count(earlier) + 1 from Waiting earlier
                 where earlier.slot = w.slot
                   and (earlier.createdAt < w.createdAt
                        or (earlier.createdAt = w.createdAt and earlier.id < w.id))))
            from Waiting w join fetch w.slot s join fetch s.time join fetch s.theme
            where w.member.id = :memberId
            order by w.createdAt, w.id
            """)
    List<WaitingWithRank> findAllWithRankByMemberId(@Param("memberId") Long memberId);

    @Query("""
            select count(w) from Waiting w
            where w.slot.id = :slotId
              and (
                  w.createdAt < :createdAt
                  or (w.createdAt = :createdAt and w.id < :waitingId)
              )
            """)
    long countEarlierWaitings(
            @Param("slotId") Long slotId,
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
        return ConstraintViolationUtils.hasConstraint(exception, "uk_waitings_member_slot");
    }
}
