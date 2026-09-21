package roomescape.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.member.Member;
import roomescape.member.MemberRepository;
import roomescape.member.exception.MemberErrorCode;
import roomescape.member.exception.MemberException;
import roomescape.reservation.exception.ReservationErrorCode;
import roomescape.reservation.exception.ReservationException;
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
import roomescape.waiting.WaitingRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final MemberRepository memberRepository;
    private final TimeRepository timeRepository;
    private final ThemeRepository themeRepository;
    private final SlotRepository slotRepository;
    private final WaitingRepository waitingRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            MemberRepository memberRepository,
            TimeRepository timeRepository,
            ThemeRepository themeRepository,
            SlotRepository slotRepository,
            WaitingRepository waitingRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.memberRepository = memberRepository;
        this.timeRepository = timeRepository;
        this.themeRepository = themeRepository;
        this.slotRepository = slotRepository;
        this.waitingRepository = waitingRepository;
    }

    @Transactional
    public ReservationResponse save(ReservationRequest reservationRequest, Long loginMemberId) {
        Member member = memberRepository.findById(loginMemberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        Time time = timeRepository.findByIdAndDeletedFalse(reservationRequest.getTime())
                .orElseThrow(() -> new TimeException(TimeErrorCode.TIME_NOT_FOUND));
        Theme theme = themeRepository.findByIdAndDeletedFalse(reservationRequest.getTheme())
                .orElseThrow(() -> new ThemeException(ThemeErrorCode.THEME_NOT_FOUND));

        String reservationName = reservationRequest.getName() != null
                && !reservationRequest.getName().isBlank()
                ? reservationRequest.getName() : member.getName();

        Slot slot = slotRepository.findForUpdateByDateAndTimeIdAndThemeId(
                        reservationRequest.getDate(), time.getId(), theme.getId())
                .orElseGet(() -> slotRepository.saveAndFlush(
                        new Slot(reservationRequest.getDate(), time, theme)));

        Reservation saved = reservationRepository.saveReservation(new Reservation(
                reservationName, slot, member));

        return ReservationResponse.from(saved);
    }

    @Transactional
    public void deleteById(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        slotRepository.findForUpdateById(reservation.getSlot().getId())
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        int deletedCount = reservationRepository.deleteByReservationId(reservationId);
        if (deletedCount == 0) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND);
        }

        waitingRepository.findFirstBySlotIdOrderByCreatedAtAscIdAsc(reservation.getSlot().getId())
                .ifPresent(waiting -> {
                    Member waitingMember = waiting.getMember();
                    reservationRepository.saveReservation(
                            new Reservation(waitingMember.getName(), reservation.getSlot(), waitingMember));
                    waitingRepository.delete(waiting);
                });
    }

    public List<ReservationResponse> findAll() {
        return reservationRepository.findAll().stream()
                .map(ReservationResponse::from)
                .toList();
    }

    public MyReservations findMyReservations(Long memberId) {
        return new MyReservations(
                reservationRepository.findAllByMemberId(memberId),
                waitingRepository.findAllWithRankByMemberId(memberId)
        );
    }
}
