package roomescape.theme;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.theme.exception.ThemeErrorCode;
import roomescape.theme.exception.ThemeException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ThemeService {
    private final ThemeRepository themeRepository;

    public ThemeService(ThemeRepository themeRepository) {
        this.themeRepository = themeRepository;
    }

    public List<Theme> findAll() {
        return themeRepository.findAllByDeletedFalse();
    }

    @Transactional
    public Theme save(Theme theme) {
        return themeRepository.save(theme);
    }

    @Transactional
    public void deleteById(Long id) {
        Theme theme = themeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ThemeException(ThemeErrorCode.THEME_NOT_FOUND));

        theme.delete();
    }
}
