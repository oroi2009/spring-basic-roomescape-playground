package roomescape.waiting;

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
    void 본인의_예약_대기를_취소한다() {
        // given
        String token = login("brown@email.com");
        Long waitingId = createWaiting(token, "2024-03-01", 1L, 1L).jsonPath().getLong("id");

        // when
        RestAssured.given()
                .cookie("token", token)
                .when().delete("/waitings/" + waitingId)
                .then().statusCode(204);

        // then
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from waitings where id = ?", Integer.class, waitingId)).isZero();
    }

    @Test
    void 다른_회원의_예약_대기는_취소할_수_없다() {
        // given
        String ownerToken = login("brown@email.com");
        String otherToken = login("admin@email.com");
        Long waitingId = createWaiting(ownerToken, "2024-03-01", 1L, 1L)
                .jsonPath().getLong("id");

        // when
        ExtractableResponse<Response> response = RestAssured.given()
                .cookie("token", otherToken)
                .when().delete("/waitings/" + waitingId)
                .then().statusCode(404)
                .extract();

        // then
        assertThat(response.jsonPath().getString("code")).isEqualTo("WAITING_NOT_FOUND");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from waitings where id = ?", Integer.class, waitingId)).isEqualTo(1);
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
                "select count(*) from waitings w join slots s on w.slot_id = s.id "
                        + "where s.date = ? and s.time_id = ? and s.theme_id = ?",
                Integer.class, "2024-03-01", 1L, 1L)).isEqualTo(2);
    }

    @Test
    void 서로_다른_회원이_같은_슬롯에_동시에_예약_대기하면_순번이_겹치지_않는다() throws Exception {
        // given
        String firstToken = createMemberAndLogin("first-waiter@email.com", "첫 번째 대기자");
        String secondToken = createMemberAndLogin("second-waiter@email.com", "두 번째 대기자");

        // when
        List<Response> responses = sendWaitingsConcurrently(
                firstToken,
                secondToken,
                "2024-03-01",
                1L,
                1L
        );
        responses.stream()
                .filter(response -> response.getStatusCode() == 201)
                .map(response -> response.jsonPath().getLong("id"))
                .forEach(waitingIds::add);

        // then
        assertThat(responses)
                .extracting(Response::getStatusCode)
                .containsExactlyInAnyOrder(201, 201);
        assertThat(responses)
                .extracting(response -> response.jsonPath().getLong("waitingNumber"))
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from waitings w join slots s on w.slot_id = s.id "
                        + "where s.date = ? and s.time_id = ? and s.theme_id = ?",
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
        return createMemberAndLogin("tester@email.com", "테스터");
    }

    private String createMemberAndLogin(String email, String name) {
        jdbcTemplate.update(
                "insert into members (name, email, password, role) values (?, ?, ?, ?)",
                name, email, "password", "USER");
        Long memberId = jdbcTemplate.queryForObject(
                "select id from members where email = ?", Long.class, email);
        memberIds.add(memberId);
        return login(email);
    }

    private List<Response> sendWaitingsConcurrently(
            String firstToken,
            String secondToken,
            String date,
            Long time,
            Long theme
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Callable<Response>> tasks = List.of(
                () -> sendWaiting(firstToken, date, time, theme, barrier),
                () -> sendWaiting(secondToken, date, time, theme, barrier)
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

    private Response sendWaiting(
            String token,
            String date,
            Long time,
            Long theme,
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
                .body(waitingRequest(date, time, theme))
                .when().post("/waitings")
                .then().extract().response();
    }

    private Response getResponse(Future<Response> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("동시 대기 요청 결과를 기다리는 중 인터럽트가 발생했습니다.", exception);
        } catch (CancellationException exception) {
            throw new AssertionError("동시 대기 요청이 제한 시간 내에 완료되지 않았습니다.", exception);
        } catch (ExecutionException exception) {
            throw new AssertionError("동시 대기 요청 결과를 가져오지 못했습니다.", exception);
        }
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
