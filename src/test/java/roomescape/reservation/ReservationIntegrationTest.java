package roomescape.reservation;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
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
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationIntegrationTest extends IntegrationTestSupport {
    private final List<Long> reservationIds = new ArrayList<>();
    private final List<Long> waitingIds = new ArrayList<>();
    private final List<String> slotDates = new ArrayList<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        for (Long id : reservationIds) {
            jdbcTemplate.update("delete from reservations where id = ?", id);
        }
        for (Long id : waitingIds) {
            jdbcTemplate.update("delete from waitings where id = ?", id);
        }
        for (String date : slotDates) {
            jdbcTemplate.update(
                    "delete from slots where date = ? and time_id = ? and theme_id = ?",
                    date, 1L, 1L);
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

    @Test
    void 로그인한_회원의_예약_목록만_조회한다() {
        // given
        String token = login("brown@email.com");

        // when
        ExtractableResponse<Response> response = RestAssured.given()
                .cookie("token", token)
                .when().get("/reservations-mine")
                .then().statusCode(200)
                .contentType(ContentType.JSON)
                .extract();

        // then
        assertThat(response.jsonPath().getList("reservations")).hasSize(1);
        assertThat(response.jsonPath().getLong("reservations[0].reservationId")).isNotNull();
        assertThat(response.jsonPath().getString("reservations[0].theme")).isEqualTo("테마2");
        assertThat(response.jsonPath().getString("reservations[0].date")).isEqualTo("2024-03-01");
        assertThat(response.jsonPath().getString("reservations[0].time")).isEqualTo("10:00");
        assertThat(response.jsonPath().getString("reservations[0].status")).isEqualTo("예약");
    }

    @Test
    void 슬롯이_없는_자리에_예약이_동시에_요청되면_하나만_성공한다() throws Exception {
        // given
        String date = "2099-01-01";
        slotDates.add(date);
        Map<String, Object> request = createReservationRequest(date);

        // when
        List<Response> responses = sendReservationsConcurrently(
                request,
                login("admin@email.com"),
                login("brown@email.com")
        );
        rememberSuccessfulReservations(responses);

        // then
        assertThat(responses)
                .extracting(Response::getStatusCode)
                .containsExactlyInAnyOrder(201, 409);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from slots where date = ? and time_id = ? and theme_id = ?",
                Integer.class, date, 1L, 1L)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from reservations r "
                        + "join slots s on r.slot_id = s.id "
                        + "where s.date = ? and s.time_id = ? and s.theme_id = ?",
                Integer.class, date, 1L, 1L)).isEqualTo(1);
    }

    @Test
    void 이미_존재하는_슬롯에_예약이_동시에_요청되면_하나만_성공한다() throws Exception {
        // given
        String date = "2099-01-02";
        slotDates.add(date);
        Map<String, Object> request = createReservationRequest(date);
        Long setupReservationId = createReservation(request, login()).jsonPath().getLong("id");
        jdbcTemplate.update("delete from reservations where id = ?", setupReservationId);

        // when
        List<Response> responses = sendReservationsConcurrently(
                request,
                login("admin@email.com"),
                login("brown@email.com")
        );
        rememberSuccessfulReservations(responses);

        // then
        assertThat(responses)
                .extracting(Response::getStatusCode)
                .containsExactlyInAnyOrder(201, 409);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from reservations r "
                        + "join slots s on r.slot_id = s.id "
                + "where s.date = ? and s.time_id = ? and s.theme_id = ?",
                Integer.class, date, 1L, 1L)).isEqualTo(1);
    }

    @Test
    void 예약_취소와_대기_신청이_동시에_실행되어도_정상_상태를_유지한다() throws Exception {
        // given
        String date = "2099-01-03";
        slotDates.add(date);
        Long reservationId = createReservation(createReservationRequest(date), login())
                .jsonPath().getLong("id");
        String waitingToken = login("brown@email.com");
        Long waitingMemberId = jdbcTemplate.queryForObject(
                "select id from members where email = ?", Long.class, "brown@email.com");

        // when
        List<Response> responses = sendCancellationAndWaitingConcurrently(
                reservationId, waitingToken, date);
        rememberSuccessfulWaitings(responses);
        rememberReservationsForDate(date);

        // then
        Response cancellationResponse = responses.get(0);
        Response waitingResponse = responses.get(1);
        assertThat(cancellationResponse.statusCode()).isEqualTo(204);
        assertThat(waitingResponse.statusCode()).isIn(201, 409);

        int reservationCount = jdbcTemplate.queryForObject(
                "select count(*) from reservations r "
                        + "join slots s on r.slot_id = s.id "
                        + "where s.date = ? and s.time_id = ? and s.theme_id = ?",
                Integer.class, date, 1L, 1L);
        int waitingCount = jdbcTemplate.queryForObject(
                "select count(*) from waitings w "
                        + "join slots s on w.slot_id = s.id "
                        + "where s.date = ? and s.time_id = ? and s.theme_id = ?",
                Integer.class, date, 1L, 1L);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from reservations where id = ?", Integer.class, reservationId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from slots where date = ? and time_id = ? and theme_id = ?",
                Integer.class, date, 1L, 1L)).isEqualTo(1);

        if (waitingResponse.statusCode() == 201) {
            assertThat(reservationCount).isEqualTo(1);
            assertThat(waitingCount).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "select r.member_id from reservations r "
                            + "join slots s on r.slot_id = s.id "
                            + "where s.date = ? and s.time_id = ? and s.theme_id = ?",
                    Long.class, date, 1L, 1L)).isEqualTo(waitingMemberId);
            return;
        }

        assertThat(waitingResponse.jsonPath().getString("code"))
                .isEqualTo("WAITING_NOT_AVAILABLE");
        assertThat(reservationCount).isZero();
        assertThat(waitingCount).isZero();
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

    private Map<String, Object> createReservationRequest() {
        return createReservationRequest("2027-08-15");
    }

    private Map<String, Object> createReservationRequest(String date) {
        Map<String, Object> request = new HashMap<>();
        request.put("date", date);
        request.put("theme", 1L);
        request.put("time", 1L);
        return request;
    }

    private List<Response> sendReservationsConcurrently(
            Map<String, Object> request,
            String firstToken,
            String secondToken
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        List<Callable<Response>> tasks = List.of(
                () -> sendReservation(request, firstToken, barrier),
                () -> sendReservation(request, secondToken, barrier)
        );

        try {
            List<Future<Response>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
            return futures.stream()
                    .map(this::getResponse)
                    .toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private Response sendReservation(
            Map<String, Object> request,
            String token,
            CyclicBarrier barrier
    ) throws InterruptedException, BrokenBarrierException, TimeoutException {
        barrier.await(5, TimeUnit.SECONDS);
        return RestAssured.given()
                .config(RestAssuredConfig.config().httpClient(
                        HttpClientConfig.httpClientConfig()
                                .setParam("http.connection.timeout", 10_000)
                                .setParam("http.socket.timeout", 10_000)
                                .setParam("http.connection-manager.timeout", 10_000)
                ))
                .cookie("token", token)
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/reservations")
                .then().extract().response();
    }

    private List<Response> sendCancellationAndWaitingConcurrently(
            Long reservationId,
            String waitingToken,
            String date
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Callable<Response>> tasks = List.of(
                () -> sendReservationDeletion(reservationId, barrier),
                () -> sendWaiting(waitingToken, date, barrier)
        );

        try {
            List<Future<Response>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
            return futures.stream()
                    .map(this::getResponse)
                    .toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private Response sendReservationDeletion(Long reservationId, CyclicBarrier barrier)
            throws InterruptedException, BrokenBarrierException, TimeoutException {
        barrier.await(5, TimeUnit.SECONDS);
        return RestAssured.given()
                .config(RestAssuredConfig.config().httpClient(
                        HttpClientConfig.httpClientConfig()
                                .setParam("http.connection.timeout", 10_000)
                                .setParam("http.socket.timeout", 10_000)
                                .setParam("http.connection-manager.timeout", 10_000)
                ))
                .when().delete("/reservations/" + reservationId)
                .then().extract().response();
    }

    private Response sendWaiting(String token, String date, CyclicBarrier barrier)
            throws InterruptedException, BrokenBarrierException, TimeoutException {
        barrier.await(5, TimeUnit.SECONDS);
        return RestAssured.given()
                .config(RestAssuredConfig.config().httpClient(
                        HttpClientConfig.httpClientConfig()
                                .setParam("http.connection.timeout", 10_000)
                                .setParam("http.socket.timeout", 10_000)
                                .setParam("http.connection-manager.timeout", 10_000)
                ))
                .cookie("token", token)
                .contentType(ContentType.JSON)
                .body(createReservationRequest(date))
                .when().post("/waitings")
                .then().extract().response();
    }

    private Response getResponse(Future<Response> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("동시 예약 요청 결과를 기다리는 중 인터럽트가 발생했습니다.", exception);
        } catch (CancellationException exception) {
            throw new AssertionError("동시 예약 요청이 제한 시간 내에 완료되지 않았습니다.", exception);
        } catch (ExecutionException exception) {
            throw new AssertionError("동시 예약 요청 결과를 가져오지 못했습니다.", exception);
        }
    }

    private void rememberSuccessfulReservations(List<Response> responses) {
        responses.stream()
                .filter(response -> response.getStatusCode() == 201)
                .map(response -> response.jsonPath().getLong("id"))
                .forEach(reservationIds::add);
    }

    private void rememberSuccessfulWaitings(List<Response> responses) {
        responses.stream()
                .filter(response -> response.getStatusCode() == 201)
                .map(response -> response.jsonPath().getLong("id"))
                .forEach(waitingIds::add);
    }

    private void rememberReservationsForDate(String date) {
        jdbcTemplate.queryForList(
                        "select r.id from reservations r "
                                + "join slots s on r.slot_id = s.id where s.date = ?",
                        Long.class, date)
                .forEach(reservationIds::add);
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
