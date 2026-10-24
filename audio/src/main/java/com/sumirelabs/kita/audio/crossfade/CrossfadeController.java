package com.sumirelabs.kita.audio.crossfade;

import com.sumirelabs.kita.common.CrossfadeState;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v4/kita/crossfade/{session}/{guild}")
public final class CrossfadeController {
    public record Start(String id, String encoded, int seconds) {}
    public record Prepare(String token, String expected, String encoded) {}
    private final CrossfadeRegistry registry;
    public CrossfadeController(CrossfadeRegistry registry) { this.registry = registry; }
    @PostMapping public CrossfadeState start(@PathVariable("session") String session, @PathVariable("guild") long guild, @RequestBody Start request) throws Exception {
        return registry.start(session, guild, request.id(), request.encoded(), request.seconds());
    }
    @GetMapping("/{id}") public CrossfadeState status(@PathVariable("session") String session, @PathVariable("guild") long guild, @PathVariable("id") String id) {
        return registry.status(session, guild, id);
    }
    @PutMapping("/{id}") public CrossfadeState prepare(@PathVariable("session") String session, @PathVariable("guild") long guild, @PathVariable("id") String id, @RequestBody Prepare request) throws Exception {
        return registry.prepare(session, guild, id, request.token(), request.expected(), request.encoded());
    }
    @DeleteMapping("/{id}/pending")
    public CrossfadeState cancel(@PathVariable("session") String session, @PathVariable("guild") long guild, @PathVariable("id") String id) {
        return registry.cancelPending(session, guild, id);
    }
    @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void stop(@PathVariable("session") String session, @PathVariable("guild") long guild, @PathVariable("id") String id) {
        registry.stop(session, guild, id);
    }
}
