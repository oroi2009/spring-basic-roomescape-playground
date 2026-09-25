package roomescape.slot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import roomescape.reservation.exception.ReservationErrorCode;
import roomescape.reservation.exception.ReservationException;
import roomescape.theme.Theme;
import roomescape.time.Time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class SlotRepositoryTest {
    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void 같은_날짜_시간_테마의_슬롯을_저장하면_예약_중복_예외로_변환한다() {
        // given
        Time time = entityManager.persist(new Time("09:00"));
        Theme theme = entityManager.persist(new Theme("테스트 테마", "테스트 설명"));
        entityManager.persist(new Slot("2099-09-01", time, theme));
        entityManager.flush();

        // when & then
        assertThatThrownBy(() -> slotRepository.saveSlot(
                new Slot("2099-09-01", time, theme)))
                .isInstanceOfSatisfying(ReservationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ReservationErrorCode.DUPLICATE_RESERVATION));
    }
}
