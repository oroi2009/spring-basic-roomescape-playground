package roomescape.waiting.exception;

import roomescape.global.exception.BusinessException;
import roomescape.global.response.code.ErrorCode;

public class WaitingException extends BusinessException {

    public WaitingException(ErrorCode errorCode) {
        super(errorCode);
    }
}
