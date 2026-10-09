package com.sumirelabs.kita.audio;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class HrirController {
    private final HrirCatalog catalog;
    public HrirController(HrirCatalog catalog) { this.catalog = catalog; }
    @GetMapping("/v4/kita/presets")
    public List<Map<String, String>> presets() {
        return catalog.presets().stream().map(p -> Map.of("id", p.id(), "name", p.name(),
                "description", p.description(), "file", p.fileName())).toList();
    }
}
