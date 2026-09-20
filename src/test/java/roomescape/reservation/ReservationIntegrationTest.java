package roomescape.reservation;

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

class ReservationIntegrationTest extends IntegrationTestSupport {
    private final List<Long> reservationIds = new ArrayList<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        for (Long id : reservationIds) {
            jdbcTemplate.update("delete from reservations where id = ?", id);
        }
    }

    @Test
    void 이름을_생략하면_로그인_회원으로_예약을_생성한다() {
        // given
        String token = login();
        Map<String, Object> request = createReservationRequest();

        // when
        ExtractableResponse<Response> response = createReservation(request, token);

        // then
        assertReservationName(response, "어드민");
    }

    @Test
    void 이름을_지정해도_로그인_회원을_예약_소유자로_저장한다() {
        // given
        String token = login();
        Map<String, Object> request = createReservationRequest();
        request.put("name", "브라운");

        // when
        ExtractableResponse<Response> response = createReservation(request, token);

        // then
        assertReservationName(response, "브라운");
        Long memberId = jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, "admin@email.com");
        assertThat(jdbcTemplate.queryForObject("select member_id from reservations where id = ?", Long.class, response.jsonPath().getLong("id")))
                .isEqualTo(memberId);
    }

    @Test
    void 예약_날짜가_누락되면_400을_반환하고_예약을_저장하지_않는다() {
        // given
        String token = login();
        Map<String, Object> request = createReservationRequest();
        request.remove("date");
        Integer beforeCount = jdbcTemplate.queryForObject("select count(*) from reservations", Integer.class);

        // when
        ExtractableResponse<Response> response = RestAssured.given()
                .cookie("token", token)
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/reservations")
                .then().statusCode(400)
                .contentType(ContentType.JSON)
                .extract();

        // then
        assertThat(response.jsonPath().getString("code")).isEqualTo("GLOBAL_BAD_REQUEST");
        assertThat(response.jsonPath().getString("message")).isEqualTo("예약 날짜는 비어 있을 수 없습니다.");
        assertThat(jdbcTemplate.queryForObject("select count(*) from reservations", Integer.class))
                .isEqualTo(beforeCount);
    }

    @Test
    void 로그인하지_않으면_예약을_생성할_수_없다() {
        // given
        Map<String, Object> request = createReservationRequest();
        request.put("name", "브라운");

        // when
        ExtractableResponse<Response> response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/reservations")
                .then().statusCode(401)
                .extract();

        // then
        assertThat(response.jsonPath().getString("code")).isEqualTo("MEMBER_LOGIN_REQUIRED");
    }

    private String login() {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "admin@email.com", "password", "password"))
                .when().post("/login")
                .then().statusCode(200)
                .extract().cookie("token");
    }

    private Map<String, Object> createReservationRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("date", "2027-08-15");
        request.put("theme", 1L);
        request.put("time", 1L);
        return request;
    }

    private ExtractableResponse<Response> createReservation(Map<String, Object> request, String token) {
        ExtractableResponse<Response> response = RestAssured.given()
                .cookie("token", token)
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/reservations")
                .then().statusCode(201)
                .contentType(ContentType.JSON)
                .extract();
        reservationIds.add(response.jsonPath().getLong("id"));
        return response;
    }

    private void assertReservationName(ExtractableResponse<Response> response, String name) {
        Long id = response.jsonPath().getLong("id");
        assertThat(response.jsonPath().getString("name")).isEqualTo(name);
        assertThat(jdbcTemplate.queryForObject("select name from reservations where id = ?", String.class, id))
                .isEqualTo(name);
    }
}
