package roomescape.waiting;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import roomescape.member.LoginMember;

import java.net.URI;

@RestController
public class WaitingController {
    private final WaitingService waitingService;

    public WaitingController(WaitingService waitingService) {
        this.waitingService = waitingService;
    }

    @PostMapping("/waitings")
    public ResponseEntity<WaitingResponse> create(
            @Valid @RequestBody WaitingRequest request,
            LoginMember loginMember
    ) {
        WaitingResponse waiting = waitingService.create(request, loginMember.id());
        return ResponseEntity.created(URI.create("/waitings/" + waiting.id())).body(waiting);
    }

    @DeleteMapping("/waitings/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            LoginMember loginMember
    ) {
        waitingService.delete(id, loginMember.id());
        return ResponseEntity.noContent().build();
    }
}
