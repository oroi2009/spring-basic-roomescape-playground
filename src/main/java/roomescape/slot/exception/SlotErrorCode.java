package roomescape.slot.exception;

import org.springframework.http.HttpStatus;
import roomescape.global.response.code.ErrorCode;

public enum SlotErrorCode implements ErrorCode {
    DUPLICATE_SLOT("SLOT_DUPLICATE", HttpStatus.CONFLICT, "해당 날짜·시간대·테마의 슬롯이 이미 생성되었습니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    SlotErrorCode(String code, HttpStatus httpStatus, String message) {
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
