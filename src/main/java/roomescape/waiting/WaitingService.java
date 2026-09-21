package roomescape.waiting;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.member.Member;
import roomescape.member.MemberRepository;
import roomescape.member.exception.MemberErrorCode;
import roomescape.member.exception.MemberException;
import roomescape.reservation.ReservationRepository;
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

    public WaitingService(
            WaitingRepository waitingRepository,
            ReservationRepository reservationRepository,
            MemberRepository memberRepository,
            TimeRepository timeRepository,
            ThemeRepository themeRepository
    ) {
        this.waitingRepository = waitingRepository;
        this.reservationRepository = reservationRepository;
        this.memberRepository = memberRepository;
        this.timeRepository = timeRepository;
        this.themeRepository = themeRepository;
    }

    @Transactional
    public WaitingResponse create(WaitingRequest request, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        Time time = timeRepository.findByIdAndDeletedFalse(request.time())
                .orElseThrow(() -> new TimeException(TimeErrorCode.TIME_NOT_FOUND));
        Theme theme = themeRepository.findByIdAndDeletedFalse(request.theme())
                .orElseThrow(() -> new ThemeException(ThemeErrorCode.THEME_NOT_FOUND));

        if (!reservationRepository.existsByDateAndTimeIdAndThemeId(
                request.date(), time.getId(), theme.getId())) {
            throw new WaitingException(WaitingErrorCode.WAITING_NOT_AVAILABLE);
        }

        if (reservationRepository.existsByDateAndTimeIdAndThemeIdAndMemberId(
                request.date(), time.getId(), theme.getId(), member.getId())) {
            throw new WaitingException(WaitingErrorCode.ALREADY_RESERVED);
        }

        Waiting waiting = waitingRepository.saveWaiting(new Waiting(request.date(), time, theme, member));
        long earlierWaitingCount = waitingRepository.countEarlierWaitings(
                waiting.getDate(),
                waiting.getTime().getId(),
                waiting.getTheme().getId(),
                waiting.getCreatedAt(),
                waiting.getId()
        );

        return WaitingResponse.from(waiting, earlierWaitingCount + 1);
    }
}
