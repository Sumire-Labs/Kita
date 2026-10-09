package com.sumirelabs.kita.tickets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumirelabs.kita.discord.ReactiveWork;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.TextChannel;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import reactor.core.publisher.Mono;

public final class TicketTranscript {
    private record Output(Path temporary, Path target, BufferedWriter writer, int[] count) {}
    private final Path root;
    private final ObjectMapper json = discord4j.common.JacksonResources.create().getObjectMapper();
    public TicketTranscript(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public Mono<Path> save(TextChannel channel) {
        return Mono.usingWhen(ReactiveWork.io(() -> {
            var directory = root.resolve(channel.getGuildId().asString()); Files.createDirectories(directory);
            var target = directory.resolve(channel.getId().asString() + ".jsonl");
            var temporary = Files.createTempFile(directory, "transcript-", ".tmp");
            try {
                var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8);
                writer.write(json.writeValueAsString(Map.of("guild", channel.getGuildId().asString(), "channel", channel.getId().asString(),
                        "order", "newest-first", "capturedAt", Instant.now().toString()))); writer.newLine();
                return new Output(temporary, target, writer, new int[1]);
            } catch (Exception error) { Files.deleteIfExists(temporary); throw error; }
        }), output -> channel.getMessagesBefore(Snowflake.of(Instant.now())).take(50_001).buffer(100)
                .concatMap(messages -> ReactiveWork.io(() -> {
                    for (var message : messages) if (output.count[0]++ < 50_000) {
                        output.writer.write(serialize(message)); output.writer.newLine();
                    }
                    return true;
                })).then(ReactiveWork.io(() -> {
                    output.writer.write(json.writeValueAsString(Map.of("complete", output.count[0] <= 50_000, "messages", Math.min(50_000, output.count[0]))));
                    output.writer.newLine(); output.writer.close();
                    Files.move(output.temporary, output.target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    return output.target;
                })), this::clean, (output, error) -> clean(output), this::clean);
    }
    private String serialize(Message message) throws Exception {
        var author = message.getAuthor();
        return json.writeValueAsString(Map.of("id", message.getId().asString(), "author", author.map(user -> user.getId().asString()).orElse("0"),
                "name", author.map(user -> user.getUsername()).orElse("Unknown"), "text", message.getContent(),
                "components", message.getComponents().stream().map(component -> json.valueToTree(component.getData()).toString()).toList(),
                "attachments", message.getAttachments().stream().map(attachment -> attachment.getUrl()).toList(), "createdAt", message.getTimestamp().toString()));
    }
    private Mono<Void> clean(Output output) {
        return ReactiveWork.io(() -> { try { output.writer.close(); } finally { Files.deleteIfExists(output.temporary); } return true; }).then();
    }
}
