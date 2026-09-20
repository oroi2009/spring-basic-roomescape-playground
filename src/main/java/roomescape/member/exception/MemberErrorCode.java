package roomescape.member.exception;

import org.springframework.http.HttpStatus;
import roomescape.global.response.code.ErrorCode;

public enum MemberErrorCode implements ErrorCode {
    LOGIN_FAILED("MEMBER_LOGIN_FAILED", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    LOGIN_REQUIRED("MEMBER_LOGIN_REQUIRED", HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    ADMIN_REQUIRED("MEMBER_ADMIN_REQUIRED", HttpStatus.UNAUTHORIZED, "관리자 권한이 필요합니다."),
    MEMBER_NOT_FOUND("MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    MemberErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
