package roomescape.reservation;

import java.util.List;

public record MyReservationsResponse(
        List<MyReservationResponse> reservations
) {
}
