package roomescape.waiting;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import roomescape.IntegrationTestSupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingIntegrationTest extends IntegrationTestSupport {
    private final List<Long> waitingIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        for (Long id : waitingIds) {
            jdbcTemplate.update("delete from waitings where id = ?", id);
        }
        for (Long id : memberIds) {
            jdbcTemplate.update("delete from members where id = ?", id);
        }
    }

    @Test
    void 예약된_시간대에_예약_대기를_생성한다() {
        // given
        String token = login("brown@email.com");

        // when
        ExtractableResponse<Response> response = createWaiting(token, "2024-03-01", 1L, 1L);

        // then
        Long id = response.jsonPath().getLong("id");
        assertThat(id).isNotNull();
        assertThat(response.jsonPath().getLong("waitingNumber")).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select created_at from waitings where id = ?", Object.class, id)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select member_id from waitings where id = ?", Long.class, id)).isEqualTo(2L);
    }

    @Test
    void 같은_회원이_같은_시간대에_중복으로_예약_대기할_수_없다() {
        // given
        String token = login("brown@email.com");
        createWaiting(token, "2024-03-01", 1L, 1L);

        // when
        ExtractableResponse<Response> response = RestAssured.given()
                .cookie("token", token)
                .contentType(ContentType.JSON)
                .body(waitingRequest("2024-03-01", 1L, 1L))
                .when().post("/waitings")
                .then().statusCode(409)
                .extract();

        // then
        assertThat(response.jsonPath().getString("code")).isEqualTo("WAITING_DUPLICATE");
    }

    @Test
    void 이미_예약한_회원은_같은_시간대에_예약_대기할_수_없다() {
        // given
        String token = login("brown@email.com");

        // when
        ExtractableResponse<Response> response = RestAssured.given()
                .cookie("token", token)
                .contentType(ContentType.JSON)
                .body(waitingRequest("2024-03-01", 1L, 2L))
                .when().post("/waitings")
                .then().statusCode(409)
                .extract();

        // then
        assertThat(response.jsonPath().getString("code")).isEqualTo("WAITING_ALREADY_RESERVED");
    }

    @Test
    void 서로_다른_회원은_같은_시간대에_예약_대기할_수_있다() {
        // given
        String brownToken = login("brown@email.com");
        String testerToken = createMemberAndLogin();

        // when
        createWaiting(brownToken, "2024-03-01", 1L, 1L);
        ExtractableResponse<Response> testerResponse = createWaiting(testerToken, "2024-03-01", 1L, 1L);

        // then
        assertThat(testerResponse.jsonPath().getLong("waitingNumber")).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from waitings where date = ? and time_id = ? and theme_id = ?",
                Integer.class, "2024-03-01", 1L, 1L)).isEqualTo(2);
    }

    @Test
    void 내_예약_목록에_본인의_예약과_대기_순번을_함께_반환한다() {
        // given
        String token = login("brown@email.com");
        String otherToken = createMemberAndLogin();
        createWaiting(otherToken, "2024-03-01", 1L, 1L);
        Long waitingId = createWaiting(token, "2024-03-01", 1L, 1L).jsonPath().getLong("id");

        // when
        ExtractableResponse<Response> response = RestAssured.given()
                .cookie("token", token)
                .when().get("/reservations-mine")
                .then().statusCode(200)
                .extract();

        // then
        assertThat(response.jsonPath().getList("reservations")).hasSize(2);
        assertThat(response.jsonPath().getList("reservations.status", String.class))
                .containsExactly("예약", "대기 2번");
        assertThat(response.jsonPath().getLong("reservations[0].reservationId")).isNotNull();
        assertThat(response.jsonPath().getLong("reservations[1].waitingId")).isEqualTo(waitingId);
    }

    @Test
    void 예약이_없는_시간대에는_예약_대기할_수_없다() {
        // given
        String token = login();

        // when
        ExtractableResponse<Response> response = RestAssured.given()
                .cookie("token", token)
                .contentType(ContentType.JSON)
                .body(waitingRequest("2024-03-01", 4L, 1L))
                .when().post("/waitings")
                .then().statusCode(409)
                .extract();

        // then
        assertThat(response.jsonPath().getString("code")).isEqualTo("WAITING_NOT_AVAILABLE");
    }

    private String login() {
        return login("admin@email.com");
    }

    private String login(String email) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "password"))
                .when().post("/login")
                .then().statusCode(200)
                .extract().cookie("token");
    }

    private String createMemberAndLogin() {
        jdbcTemplate.update(
                "insert into members (name, email, password, role) values (?, ?, ?, ?)",
                "테스터", "tester@email.com", "password", "USER");
        Long memberId = jdbcTemplate.queryForObject(
                "select id from members where email = ?", Long.class, "tester@email.com");
        memberIds.add(memberId);
        return login("tester@email.com");
    }

    private ExtractableResponse<Response> createWaiting(String token, String date, Long time, Long theme) {
        ExtractableResponse<Response> response = RestAssured.given()
                .cookie("token", token)
                .contentType(ContentType.JSON)
                .body(waitingRequest(date, time, theme))
                .when().post("/waitings")
                .then().statusCode(201)
                .extract();
        waitingIds.add(response.jsonPath().getLong("id"));
        return response;
    }

    private Map<String, Object> waitingRequest(String date, Long time, Long theme) {
        Map<String, Object> request = new HashMap<>();
        request.put("date", date);
        request.put("time", time);
        request.put("theme", theme);
        return request;
    }
}
