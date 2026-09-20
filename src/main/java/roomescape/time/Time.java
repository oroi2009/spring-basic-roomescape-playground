package roomescape.time;

import jakarta.persistence.*;

@Entity
@Table(name = "times")
public class Time {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "time_value", nullable = false, length = 20)
    private String value;

    @Column(nullable = false)
    private boolean deleted = false;

    protected Time() {
    }

    public Time(String value) {
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public String getValue() {
        return value;
    }

    public void delete() {
        this.deleted = true;
    }
}
