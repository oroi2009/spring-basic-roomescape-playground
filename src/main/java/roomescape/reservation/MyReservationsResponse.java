package roomescape.reservation;

import java.util.List;
import java.util.stream.Stream;

public record MyReservationsResponse(
        List<MyReservationResponse> reservations
) {
    public static MyReservationsResponse from(MyReservations result) {
        Stream<MyReservationResponse> reservations = result.reservations().stream()
                .map(MyReservationResponse::from);
        Stream<MyReservationResponse> waitings = result.waitings().stream()
                .map(MyReservationResponse::from);

        return new MyReservationsResponse(Stream.concat(reservations, waitings).toList());
    }
}
