package roomescape.waiting;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.member.Member;
import roomescape.member.MemberRepository;
import roomescape.member.exception.MemberErrorCode;
import roomescape.member.exception.MemberException;
import roomescape.reservation.ReservationRepository;
import roomescape.slot.Slot;
import roomescape.slot.SlotRepository;
import roomescape.theme.Theme;
import roomescape.theme.ThemeRepository;
import roomescape.theme.exception.ThemeErrorCode;
import roomescape.theme.exception.ThemeException;
import roomescape.time.Time;
import roomescape.time.TimeRepository;
import roomescape.time.exception.TimeErrorCode;
import roomescape.time.exception.TimeException;
import roomescape.waiting.exception.WaitingErrorCode;
import roomescape.waiting.exception.WaitingException;

@Service
@Transactional(readOnly = true)
public class WaitingService {
    private final WaitingRepository waitingRepository;
    private final ReservationRepository reservationRepository;
    private final MemberRepository memberRepository;
    private final TimeRepository timeRepository;
    private final ThemeRepository themeRepository;
    private final SlotRepository slotRepository;

    public WaitingService(
            WaitingRepository waitingRepository,
            ReservationRepository reservationRepository,
            MemberRepository memberRepository,
            TimeRepository timeRepository,
            ThemeRepository themeRepository,
            SlotRepository slotRepository
    ) {
        this.waitingRepository = waitingRepository;
        this.reservationRepository = reservationRepository;
        this.memberRepository = memberRepository;
        this.timeRepository = timeRepository;
        this.themeRepository = themeRepository;
        this.slotRepository = slotRepository;
    }

    @Transactional
    public WaitingResponse create(WaitingRequest request, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        Time time = timeRepository.findByIdAndDeletedFalse(request.time())
                .orElseThrow(() -> new TimeException(TimeErrorCode.TIME_NOT_FOUND));
        Theme theme = themeRepository.findByIdAndDeletedFalse(request.theme())
                .orElseThrow(() -> new ThemeException(ThemeErrorCode.THEME_NOT_FOUND));

        Slot slot = slotRepository.findForUpdateByDateAndTimeIdAndThemeId(
                        request.date(), time.getId(), theme.getId())
                .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_NOT_AVAILABLE));

        if (!reservationRepository.existsBySlotId(slot.getId())) {
            throw new WaitingException(WaitingErrorCode.WAITING_NOT_AVAILABLE);
        }

        if (reservationRepository.existsBySlotIdAndMemberId(slot.getId(), member.getId())) {
            throw new WaitingException(WaitingErrorCode.ALREADY_RESERVED);
        }

        Waiting waiting = waitingRepository.saveWaiting(new Waiting(slot, member));
        long earlierWaitingCount = waitingRepository.countEarlierWaitings(
                waiting.getSlot().getId(),
                waiting.getCreatedAt(),
                waiting.getId()
        );

        return WaitingResponse.from(waiting, earlierWaitingCount + 1);
    }

    @Transactional
    public void delete(Long waitingId, Long memberId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_NOT_FOUND));
        slotRepository.findForUpdateById(waiting.getSlot().getId())
                .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_NOT_FOUND));

        int deletedCount = waitingRepository.deleteByIdAndMemberId(waitingId, memberId);
        if (deletedCount == 0) {
            throw new WaitingException(WaitingErrorCode.WAITING_NOT_FOUND);
        }
    }
}
