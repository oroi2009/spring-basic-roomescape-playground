package roomescape.member;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import roomescape.IntegrationTestSupport;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

class AdminAccessIntegrationTest extends IntegrationTestSupport {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @ValueSource(strings = {"/manager/times", "/manager/themes"})
    void 일반_회원은_관리자_API로_생성하거나_삭제할_수_없다(String path) {
        // given
        String token = login("brown@email.com");

        // when & then
        RestAssured.given().cookie("token", token)
                .contentType("application/json").body(createRequest(path))
                .when().post(path).then().statusCode(401)
                .body("code", equalTo("MEMBER_ADMIN_REQUIRED"))
                .body("message", equalTo("관리자 권한이 필요합니다."));
        RestAssured.given().cookie("token", token)
                .when().delete(path + "/-1").then().statusCode(401)
                .body("code", equalTo("MEMBER_ADMIN_REQUIRED"))
                .body("message", equalTo("관리자 권한이 필요합니다."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/manager/times", "/manager/themes"})
    void 미로그인_사용자는_관리자_API로_생성하거나_삭제할_수_없다(String path) {
        // when & then
        RestAssured.given().contentType("application/json").body(createRequest(path))
                .when().post(path).then().statusCode(401)
                .body("code", equalTo("MEMBER_LOGIN_REQUIRED"))
                .body("message", equalTo("로그인이 필요합니다."));
        RestAssured.given()
                .when().delete(path + "/-1").then().statusCode(401)
                .body("code", equalTo("MEMBER_LOGIN_REQUIRED"))
                .body("message", equalTo("로그인이 필요합니다."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/manager/times", "/manager/themes"})
    void 관리자는_API로_생성하고_삭제할_수_있다(String path) {
        // given
        String token = login("admin@email.com");

        // when & then
        Long id = RestAssured.given().cookie("token", token)
                .contentType("application/json").body(createRequest(path))
                .when().post(path).then().statusCode(201)
                .extract().jsonPath().getLong("id");
        try {
            RestAssured.given().cookie("token", token)
                    .when().delete(path + "/" + id).then().statusCode(204);
        } finally {
            if (path.equals("/manager/times")) {
                jdbcTemplate.update("delete from times where id = ?", id);
            } else {
                jdbcTemplate.update("delete from themes where id = ?", id);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/times", "/themes"})
    void 일반_조회_API는_로그인하지_않아도_이용할_수_있다(String path) {
        // when & then
        RestAssured.given().when().get(path)
                .then().statusCode(200).contentType("application/json");
    }

    private Map<String, String> createRequest(String path) {
        if (path.equals("/manager/times")) {
            return Map.of("value", "23:40");
        }
        return Map.of("name", "권한검증테마", "description", "관리자 API 테스트");
    }


    @ParameterizedTest
    @ValueSource(strings = {"/manager", "/manager/reservation", "/manager/theme", "/manager/time"})
    void 관리자_권한이_있으면_어드민_페이지와_HTML을_반환한다(String path) {
        // given
        String token = login("admin@email.com");

        // when & then
        RestAssured.given()
                .cookie("token", token)
                .when().get(path)
                .then().statusCode(200)
                .contentType(containsString("text/html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/manager", "/manager/reservation", "/manager/theme", "/manager/time"})
    void 관리자_권한이_없으면_어드민_페이지_접근_시_401을_반환한다(String path) {
        // given
        String token = login("brown@email.com");

        // when & then
        RestAssured.given()
                .cookie("token", token)
                .when().get(path)
                .then().statusCode(401)
                .body("code", equalTo("MEMBER_ADMIN_REQUIRED"))
                .body("message", equalTo("관리자 권한이 필요합니다."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/manager", "/manager/reservation", "/manager/theme", "/manager/time"})
    void 로그인하지_않으면_어드민_페이지_접근_시_401을_반환한다(String path) {
        // when & then
        RestAssured.given()
                .when().get(path)
                .then().statusCode(401)
                .body("code", equalTo("MEMBER_LOGIN_REQUIRED"))
                .body("message", equalTo("로그인이 필요합니다."));
    }

    @Test
    void 로그인_페이지는_로그인하지_않아도_접근할_수_있다() {
        // when & then
        RestAssured.given()
                .when().get("/login")
                .then().statusCode(200)
                .contentType(containsString("text/html"));
    }

    private String login(String email) {
        return RestAssured.given()
                .contentType("application/json")
                .body(Map.of("email", email, "password", "password"))
                .when().post("/login")
                .then().statusCode(200)
                .extract().cookie("token");
    }
}
