package roomescape.reservation;

import roomescape.waiting.Waiting;
import roomescape.waiting.WaitingWithRank;

public record MyReservationResponse(
        Long reservationId,
        Long waitingId,
        String theme,
        String date,
        String time,
        String status
) {
    public static MyReservationResponse from(Reservation reservation) {
        return new MyReservationResponse(
                reservation.getId(),
                null,
                reservation.getTheme().getName(),
                reservation.getDate(),
                reservation.getTime().getValue(),
                "예약"
        );
    }

    public static MyReservationResponse from(WaitingWithRank waitingWithRank) {
        Waiting waiting = waitingWithRank.waiting();
        return new MyReservationResponse(
                null,
                waiting.getId(),
                waiting.getTheme().getName(),
                waiting.getDate(),
                waiting.getTime().getValue(),
                "대기 " + waitingWithRank.rank() + "번"
        );
    }
}
