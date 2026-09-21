package roomescape.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import roomescape.member.session.MemberSessionStore;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import roomescape.member.LoginMember;
import roomescape.theme.Theme;
import roomescape.time.Time;
import roomescape.waiting.Waiting;
import roomescape.waiting.WaitingWithRank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(MemberSessionStore.class)
@WebMvcTest(ReservationController.class)
class ReservationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReservationService reservationService;

    @Test
    void 내_예약_목록_조회시_로그인_회원_ID를_전달하고_응답을_반환한다() throws Exception {
        // given
        Reservation reservation = mock(Reservation.class);
        given(reservation.getId()).willReturn(4L);
        given(reservation.getTheme()).willReturn(new Theme("테마2", "테마 설명"));
        given(reservation.getTime()).willReturn(new Time("10:00"));
        given(reservation.getDate()).willReturn("2024-03-01");
        Waiting waiting = mock(Waiting.class);
        given(waiting.getId()).willReturn(5L);
        given(waiting.getTheme()).willReturn(new Theme("테마1", "테마 설명"));
        given(waiting.getTime()).willReturn(new Time("12:00"));
        given(waiting.getDate()).willReturn("2024-03-01");
        MyReservations result = new MyReservations(
                List.of(reservation), List.of(new WaitingWithRank(waiting, 2L)));
        given(reservationService.findMyReservations(1L)).willReturn(result);

        // when & then
        mockMvc.perform(get("/reservations-mine").session(loginSession()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reservations", hasSize(2)))
                .andExpect(jsonPath("$.reservations[0].reservationId").value(4))
                .andExpect(jsonPath("$.reservations[0].theme").value("테마2"))
                .andExpect(jsonPath("$.reservations[0].date").value("2024-03-01"))
                .andExpect(jsonPath("$.reservations[0].time").value("10:00"))
                .andExpect(jsonPath("$.reservations[0].status").value("예약"))
                .andExpect(jsonPath("$.reservations[1].waitingId").value(5))
                .andExpect(jsonPath("$.reservations[1].status").value("대기 2번"));
        then(reservationService).should().findMyReservations(1L);
    }

    @Test
    void 예약_생성_요청과_로그인_회원_ID를_서비스에_전달한다() throws Exception {
        // given
        Map<String, Object> request = createReservationRequest();
        request.put("name", "브라운");
        MockHttpSession session = loginSession();
        given(reservationService.save(any(ReservationRequest.class), eq(1L)))
                .willReturn(new ReservationResponse(10L, "브라운", "테마1", "2027-08-15", "10:00"));

        // when & then
        mockMvc.perform(post("/reservations").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/reservations/10"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("브라운"))
                .andExpect(jsonPath("$.date").value("2027-08-15"))
                .andExpect(jsonPath("$.theme").value("테마1"))
                .andExpect(jsonPath("$.time").value("10:00"));
        then(reservationService).should().save(argThat(value -> "브라운".equals(value.getName())
                && "2027-08-15".equals(value.getDate())
                && Long.valueOf(1L).equals(value.getTheme())
                && Long.valueOf(1L).equals(value.getTime())), eq(1L));
    }

    @ParameterizedTest
    @CsvSource({"date, 예약 날짜는 비어 있을 수 없습니다.", "theme, 예약 테마는 비어 있을 수 없습니다.",
            "time, 예약 시간은 비어 있을 수 없습니다."})
    void 필수값이_null이면_400과_검증_메시지를_반환한다(String field, String message) throws Exception {
        // given
        Map<String, Object> request = createReservationRequest();
        request.put(field, null);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loginMember", new LoginMember(1L, "어드민", "ADMIN"));

        // when & then
        mockMvc.perform(post("/reservations").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(message));
        then(reservationService).shouldHaveNoInteractions();
    }

    private MockHttpSession loginSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loginMember", new LoginMember(1L, "어드민", "ADMIN"));
        return session;
    }

    private Map<String, Object> createReservationRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("date", "2027-08-15");
        request.put("theme", 1L);
        request.put("time", 1L);
        return request;
    }
}
