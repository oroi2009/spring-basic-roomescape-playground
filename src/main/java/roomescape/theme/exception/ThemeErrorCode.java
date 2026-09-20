package roomescape.theme.exception;

import org.springframework.http.HttpStatus;
import roomescape.global.response.code.ErrorCode;

public enum ThemeErrorCode implements ErrorCode {
    THEME_NOT_FOUND("THEME_NOT_FOUND", HttpStatus.NOT_FOUND, "테마를 찾을 수 없습니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    ThemeErrorCode(String code, HttpStatus httpStatus, String message) {
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
