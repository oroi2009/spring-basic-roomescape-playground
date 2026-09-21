package roomescape.reservation;

import roomescape.waiting.WaitingWithRank;

import java.util.List;

public record MyReservations(
        List<Reservation> reservations,
        List<WaitingWithRank> waitings
) {
}
