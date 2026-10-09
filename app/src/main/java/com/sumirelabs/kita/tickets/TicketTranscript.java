package com.sumirelabs.kita.tickets;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public final class TicketTranscript {
    private final Path root;
    private final ObjectMapper json = new ObjectMapper();
    public TicketTranscript(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public Path save(TextChannel channel) throws Exception {
        var directory = root.resolve(channel.getGuild().getId());
        Files.createDirectories(directory);
        var temporary = Files.createTempFile(directory, "transcript-", ".tmp");
        var target = directory.resolve(channel.getId() + ".jsonl");
        boolean complete = false;
        try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            writer.write(json.writeValueAsString(Map.of("guild", channel.getGuild().getId(), "channel", channel.getId(),
                    "order", "newest-first", "capturedAt", java.time.Instant.now().toString())));
            writer.newLine();
            var history = channel.getHistory();
            int count = 0;
            while (count < 50_000) {
                var batch = history.retrievePast(100).complete();
                if (batch.isEmpty()) { complete = true; break; }
                for (var message : batch) {
                    writer.write(json.writeValueAsString(Map.of("id", message.getId(), "author", message.getAuthor().getId(),
                            "name", message.getAuthor().getName(), "text", message.getContentRaw(),
                            "components", new net.dv8tion.jda.api.components.utils.ComponentSerializer()
                                    .serializeAll(message.getComponents()).stream().map(Object::toString).toList(),
                            "attachments", message.getAttachments().stream().map(a -> a.getUrl()).toList(),
                            "createdAt", message.getTimeCreated().toString())));
                    writer.newLine(); count++;
                }
            }
            writer.write(json.writeValueAsString(Map.of("complete", complete, "messages", count)));
            writer.newLine();
        } catch (Exception error) { Files.deleteIfExists(temporary); throw error; }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }
}
