package roomescape.slot.exception;

import roomescape.global.exception.BusinessException;
import roomescape.global.response.code.ErrorCode;

public class SlotException extends BusinessException {

    public SlotException(ErrorCode errorCode) {
        super(errorCode);
    }
}
