package roomescape.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.junit.jupiter.api.BeforeEach;
import roomescape.member.Member;
import roomescape.theme.Theme;
import roomescape.time.Time;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReservationRepositoryTest {
    private static final String RESERVATION_DATE = "2027-08-15";

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Reservation reservation;
    private Theme theme;

    @BeforeEach
    void setUp() {
        Member member = new Member("테스터", "test@email.com", "password", "USER");
        Time time = new Time("09:00");
        theme = new Theme("테스트 테마", "예약 저장소 테스트용 테마");

        entityManager.persist(member);
        entityManager.persist(time);
        entityManager.persist(theme);

        reservation = new Reservation("테스터", RESERVATION_DATE, time, theme, member);
        entityManager.persist(reservation);
        entityManager.flush();
    }

    @Test
    void 시간대와_테마가_삭제되어도_기존_예약을_조회한다() {
        // given
        reservation.getTime().delete();
        theme.delete();
        entityManager.flush();
        entityManager.clear();

        // when
        List<Reservation> reservations = reservationRepository.findAll();

        // then
        assertThat(reservations).anySatisfy(found -> {
            assertThat(found.getId()).isEqualTo(reservation.getId());
            assertThat(found.getTime().getValue()).isEqualTo("09:00");
            assertThat(found.getTheme().getName()).isEqualTo("테스트 테마");
        });
    }

    @Test
    void 날짜와_테마에_해당하는_예약을_조회한다() {
        // when
        List<Reservation> reservations = reservationRepository.findByDateAndThemeId(RESERVATION_DATE, theme.getId());

        // then
        assertThat(reservations).hasSize(1);
        assertThat(reservations.get(0).getId()).isEqualTo(reservation.getId());
        assertThat(reservations.get(0).getTime().getValue()).isEqualTo("09:00");
    }
}
