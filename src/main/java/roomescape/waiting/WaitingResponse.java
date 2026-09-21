package roomescape.waiting;

public record WaitingResponse(
        Long id,
        Long waitingNumber
) {
    public static WaitingResponse from(Waiting waiting, Long waitingNumber) {
        return new WaitingResponse(waiting.getId(), waitingNumber);
    }
}
