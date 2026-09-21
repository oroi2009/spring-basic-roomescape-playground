package roomescape.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import roomescape.member.Member;
import roomescape.reservation.exception.ReservationErrorCode;
import roomescape.reservation.exception.ReservationException;
import roomescape.theme.Theme;
import roomescape.theme.exception.ThemeErrorCode;
import roomescape.theme.exception.ThemeException;
import roomescape.time.Time;
import roomescape.time.exception.TimeErrorCode;
import roomescape.time.exception.TimeException;
import roomescape.waiting.Waiting;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(ReservationService.class)
class ReservationServiceTest {
    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager entityManager;

    private Member member;
    private Reservation reservation;
    private Waiting waiting;
    private Waiting earlierWaiting;

    @Test
    void 이름이_없으면_로그인_회원으로_예약을_저장하고_이름을_반환한다() {
        // given
        ReservationRequest request = createReservationRequest(null);
        Long memberId = jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, "brown@email.com");

        // when
        ReservationResponse response = reservationService.save(request, memberId);

        // then
        assertThat(response.getName()).isEqualTo("브라운");
        assertThat(jdbcTemplate.queryForObject("select name from reservations where id = ?", String.class, response.getId()))
                .isEqualTo("브라운");
    }

    @Test
    void 이름을_지정해도_로그인_회원을_예약_소유자로_저장한다() {
        // given
        ReservationRequest request = createReservationRequest("게스트");
        Long memberId = jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, "admin@email.com");

        // when
        ReservationResponse response = reservationService.save(request, memberId);

        // then
        assertThat(response.getName()).isEqualTo("게스트");
        assertThat(jdbcTemplate.queryForObject("select member_id from reservations where id = ?", Long.class, response.getId()))
                .isEqualTo(memberId);
        assertThat(jdbcTemplate.queryForObject("select name from reservations where id = ?", String.class, response.getId()))
                .isEqualTo("게스트");
    }

    @Test
    void 동일한_날짜_시간_테마로_예약하면_중복_예약_예외를_던진다() {
        // given
        ReservationRequest request = createReservationRequest(null);
        reservationService.save(request, 1L);

        // when & then
        assertThatThrownBy(() -> reservationService.save(request, 1L))
                .isInstanceOfSatisfying(ReservationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ReservationErrorCode.DUPLICATE_RESERVATION));
    }

    @Test
    void 삭제된_시간대로는_예약할_수_없다() {
        // given
        jdbcTemplate.update("update times set deleted = true where id = 1");
        ReservationRequest request = createReservationRequest(null);

        // when & then
        assertThatThrownBy(() -> reservationService.save(request, 1L))
                .isInstanceOfSatisfying(TimeException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(TimeErrorCode.TIME_NOT_FOUND));
    }

    @Test
    void 삭제된_테마로는_예약할_수_없다() {
        // given
        jdbcTemplate.update("update themes set deleted = true where id = 1");
        ReservationRequest request = createReservationRequest(null);

        // when & then
        assertThatThrownBy(() -> reservationService.save(request, 1L))
                .isInstanceOfSatisfying(ThemeException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ThemeErrorCode.THEME_NOT_FOUND));
    }

    @Test
    void 본인의_예약과_대기를_순서대로_반환하고_다른_회원의_대기도_순번에_반영한다() {
        // given
        createReservationsAndWaitings();

        // when
        MyReservations result = reservationService.findMyReservations(member.getId());

        // then
        entityManager.clear();
        assertThat(result.reservations()).extracting(Reservation::getId)
                .containsExactly(reservation.getId());
        assertThat(result.waitings()).hasSize(1);
        assertThat(result.waitings().get(0).waiting().getId()).isEqualTo(waiting.getId());
        assertThat(result.waitings().get(0).rank()).isEqualTo(2L);
        assertThat(result.reservations().get(0).getTheme().getName()).isEqualTo("테스트 테마");
        assertThat(result.reservations().get(0).getTime().getValue()).isEqualTo("09:00");
        assertThat(result.waitings().get(0).waiting().getTheme().getName()).isEqualTo("테스트 테마");
        assertThat(result.waitings().get(0).waiting().getTime().getValue()).isEqualTo("09:00");
    }

    @Test
    void 앞선_대기가_삭제되면_조회한_순번이_당겨진다() {
        // given
        createReservationsAndWaitings();
        entityManager.remove(entityManager.find(Waiting.class, earlierWaiting.getId()));
        entityManager.flush();
        entityManager.clear();

        // when
        MyReservations result = reservationService.findMyReservations(member.getId());

        // then
        assertThat(result.waitings().get(0).rank()).isEqualTo(1L);
    }

    @Test
    void 예약과_대기가_없으면_빈_목록을_반환한다() {
        // given
        Member emptyMember = entityManager.persist(new Member("빈회원", "empty@email.com", "password", "USER"));

        // when
        MyReservations result = reservationService.findMyReservations(emptyMember.getId());

        // then
        assertThat(result.reservations()).isEmpty();
        assertThat(result.waitings()).isEmpty();
    }

    private void createReservationsAndWaitings() {
        member = entityManager.persist(new Member("테스터", "test@email.com", "password", "USER"));
        Member otherMember = entityManager.persist(new Member("다른회원", "other@email.com", "password", "USER"));
        Member earlierMember = entityManager.persist(new Member("대기회원", "earlier@email.com", "password", "USER"));
        Time time = entityManager.persist(new Time("09:00"));
        Theme theme = entityManager.persist(new Theme("테스트 테마", "테마 설명"));
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 21, 10, 0);

        reservation = entityManager.persist(new Reservation("테스터", "2027-08-16", time, theme, member));
        entityManager.persist(new Reservation("다른회원", "2027-08-15", time, theme, otherMember));
        earlierWaiting = entityManager.persist(new Waiting("2027-08-15", time, theme, earlierMember, createdAt));
        waiting = entityManager.persist(new Waiting("2027-08-15", time, theme, member, createdAt));
        entityManager.persist(new Waiting("2027-08-16", time, theme, otherMember, createdAt.minusDays(1)));
        entityManager.flush();
        entityManager.clear();
    }

    private ReservationRequest createReservationRequest(String name) {
        Map<String, Object> values = new HashMap<>(Map.of("date", "2027-08-15", "theme", 1L, "time", 1L));
        values.put("name", name);
        return new ObjectMapper().convertValue(values, ReservationRequest.class);
    }
}
