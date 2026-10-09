package com.sumirelabs.kita.discord;

import discord4j.core.object.component.Container;
import discord4j.core.object.component.ICanBeUsedInContainerComponent;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.component.TopLevelMessageComponent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.core.spec.MessageCreateFields;
import discord4j.rest.util.AllowedMentions;
import java.io.ByteArrayInputStream;
import java.util.List;

public final class Ui {
    private Ui() {}
    public static Container append(Container container, ICanBeUsedInContainerComponent... components) { return container.withAddedComponents(components); }
    public static MessageCreateSpec text(String title, String body) { return message(List.of(Container.of(TextDisplay.of("## " + title + "\n" + body)))); }
    public static MessageCreateSpec longText(String title, String body) {
        var container = Container.of(TextDisplay.of("## " + title));
        for (int offset = 0; offset < body.length(); offset += 3500) container = append(container,
                TextDisplay.of(body.substring(offset, Math.min(body.length(), offset + 3500))));
        return message(List.of(container));
    }
    public static MessageCreateSpec message(List<? extends TopLevelMessageComponent> components) {
        return MessageCreateSpec.builder().components(components).allowedMentions(AllowedMentions.builder().repliedUser(false).build())
                .flags(Message.Flag.IS_COMPONENTS_V2, Message.Flag.SUPPRESS_NOTIFICATIONS).build();
    }
    public static MessageCreateSpec file(MessageCreateSpec message, String name, byte[] bytes) {
        return message.withFiles(MessageCreateFields.File.of(name, new ByteArrayInputStream(bytes)));
    }
    public static MessageEditSpec edit(MessageCreateSpec message) {
        return MessageEditSpec.builder().components(discord4j.discordjson.possible.Possible.of(message.components().toOptional()))
                .allowedMentions(discord4j.discordjson.possible.Possible.of(java.util.Optional.of(AllowedMentions.builder().repliedUser(false).build())))
                .flags(discord4j.discordjson.possible.Possible.of(java.util.Optional.of(List.of(Message.Flag.IS_COMPONENTS_V2)))).build();
    }
    public static String safe(String text) { return text.replace("@", "＠").replace("`", "ˋ"); }
    public static String escape(String text) { return safe(text).replaceAll("([\\\\*_~\\[\\]()<>|])", "\\\\$1"); }
}
