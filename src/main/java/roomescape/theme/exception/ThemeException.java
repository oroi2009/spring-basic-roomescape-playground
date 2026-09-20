package roomescape.theme.exception;

import roomescape.global.exception.BusinessException;
import roomescape.global.response.code.ErrorCode;

public class ThemeException extends BusinessException {

    public ThemeException(ErrorCode errorCode) {
        super(errorCode);
    }
}
