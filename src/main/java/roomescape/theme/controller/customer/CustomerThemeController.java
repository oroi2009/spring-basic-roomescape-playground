package roomescape.theme.controller.customer;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import roomescape.theme.Theme;
import roomescape.theme.ThemeService;

import java.util.List;

@RestController
public class CustomerThemeController {
    private final ThemeService themeService;

    public CustomerThemeController(ThemeService themeService) {
        this.themeService = themeService;
    }

    @GetMapping("/themes")
    public ResponseEntity<List<Theme>> list() {
        return ResponseEntity.ok(themeService.findAll());
    }
}
