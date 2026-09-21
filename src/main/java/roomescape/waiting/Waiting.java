package roomescape.waiting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import roomescape.member.Member;
import roomescape.slot.Slot;
import roomescape.theme.Theme;
import roomescape.time.Time;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "waitings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_waitings_member_slot",
                        columnNames = {"member_id", "slot_id"}
                )
        }
)
public class Waiting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    protected Waiting() {
    }

    public Waiting(Slot slot, Member member) {
        this(slot, member, LocalDateTime.now());
    }

    public Waiting(Slot slot, Member member, LocalDateTime createdAt) {
        this.slot = slot;
        this.member = member;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getDate() {
        return slot.getDate();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Time getTime() {
        return slot.getTime();
    }

    public Theme getTheme() {
        return slot.getTheme();
    }

    public Slot getSlot() {
        return slot;
    }

    public Member getMember() {
        return member;
    }
}
