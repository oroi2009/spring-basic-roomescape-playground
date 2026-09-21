package roomescape.waiting.exception;

import org.springframework.http.HttpStatus;
import roomescape.global.response.code.ErrorCode;

public enum WaitingErrorCode implements ErrorCode {
    WAITING_NOT_AVAILABLE("WAITING_NOT_AVAILABLE", HttpStatus.CONFLICT, "예약 대기를 신청할 수 없는 시간대입니다."),
    ALREADY_RESERVED("WAITING_ALREADY_RESERVED", HttpStatus.CONFLICT, "이미 예약한 시간에는 예약 대기를 신청할 수 없습니다."),
    DUPLICATE_WAITING("WAITING_DUPLICATE", HttpStatus.CONFLICT, "이미 예약 대기를 신청한 시간입니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    WaitingErrorCode(String code, HttpStatus httpStatus, String message) {
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
