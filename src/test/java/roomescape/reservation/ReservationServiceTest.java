package roomescape.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import roomescape.reservation.exception.ReservationErrorCode;
import roomescape.reservation.exception.ReservationException;
import roomescape.theme.exception.ThemeErrorCode;
import roomescape.theme.exception.ThemeException;
import roomescape.time.exception.TimeErrorCode;
import roomescape.time.exception.TimeException;

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

    private ReservationRequest createReservationRequest(String name) {
        Map<String, Object> values = new HashMap<>(Map.of("date", "2027-08-15", "theme", 1L, "time", 1L));
        values.put("name", name);
        return new ObjectMapper().convertValue(values, ReservationRequest.class);
    }
}
