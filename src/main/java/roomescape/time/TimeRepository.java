package roomescape.time;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimeRepository extends JpaRepository<Time, Long> {

    List<Time> findAllByDeletedFalse();

    Optional<Time> findByIdAndDeletedFalse(Long id);
}
