package roomescape.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.member.Member;
import roomescape.member.MemberRepository;
import roomescape.member.exception.MemberErrorCode;
import roomescape.member.exception.MemberException;
import roomescape.theme.Theme;
import roomescape.theme.ThemeRepository;
import roomescape.theme.exception.ThemeErrorCode;
import roomescape.theme.exception.ThemeException;
import roomescape.time.Time;
import roomescape.time.TimeRepository;
import roomescape.time.exception.TimeErrorCode;
import roomescape.time.exception.TimeException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final MemberRepository memberRepository;
    private final TimeRepository timeRepository;
    private final ThemeRepository themeRepository;

    public ReservationService(ReservationRepository reservationRepository, MemberRepository memberRepository,
                              TimeRepository timeRepository, ThemeRepository themeRepository) {
        this.reservationRepository = reservationRepository;
        this.memberRepository = memberRepository;
        this.timeRepository = timeRepository;
        this.themeRepository = themeRepository;
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
                ? reservationRequest.getName() : member.getName();

        Reservation saved = reservationRepository.saveReservation(new Reservation(
                reservationName, reservationRequest.getDate(), time, theme, member));

        return ReservationResponse.from(saved);
    }

    @Transactional
    public void deleteById(Long id) {
        reservationRepository.deleteById(id);
    }

    public List<ReservationResponse> findAll() {
        return reservationRepository.findAll().stream()
                .map(ReservationResponse::from)
                .toList();
    }
}
