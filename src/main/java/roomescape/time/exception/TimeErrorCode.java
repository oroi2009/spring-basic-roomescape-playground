package roomescape.time.exception;

import org.springframework.http.HttpStatus;
import roomescape.global.response.code.ErrorCode;

public enum TimeErrorCode implements ErrorCode {
    TIME_NOT_FOUND("TIME_NOT_FOUND", HttpStatus.NOT_FOUND, "시간대를 찾을 수 없습니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    TimeErrorCode(String code, HttpStatus httpStatus, String message) {
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
