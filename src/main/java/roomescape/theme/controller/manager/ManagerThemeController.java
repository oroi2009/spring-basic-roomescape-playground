package roomescape.theme.controller.manager;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import roomescape.theme.Theme;
import roomescape.theme.ThemeService;

import java.net.URI;

@RestController
public class ManagerThemeController {
    private final ThemeService themeService;

    public ManagerThemeController(ThemeService themeService) {
        this.themeService = themeService;
    }

    @PostMapping("/manager/themes")
    public ResponseEntity<Theme> createTheme(@RequestBody Theme theme) {
        Theme newTheme = themeService.save(theme);
        return ResponseEntity.created(URI.create("/manager/themes/" + newTheme.getId())).body(newTheme);
    }

    @DeleteMapping("/manager/themes/{id}")
    public ResponseEntity<Void> deleteTheme(@PathVariable Long id) {
        themeService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
