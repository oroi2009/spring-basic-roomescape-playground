package roomescape.waiting;

import jakarta.validation.constraints.NotNull;

public record WaitingRequest(
        @NotNull(message = "예약 날짜는 비어 있을 수 없습니다.")
        String date,
        @NotNull(message = "예약 테마는 비어 있을 수 없습니다.")
        Long theme,
        @NotNull(message = "예약 시간은 비어 있을 수 없습니다.")
        Long time
) {
}
