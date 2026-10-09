package com.sumirelabs.kita.discord;

import java.util.List;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

public final class Ui {
    private Ui() {}

    public static Container append(Container container,
            net.dv8tion.jda.api.components.container.ContainerChildComponent... components) {
        var children = new java.util.ArrayList<net.dv8tion.jda.api.components.container.ContainerChildComponent>(container.getComponents());
        children.addAll(List.of(components));
        return container.withComponents(children);
    }

    public static MessageCreateData text(String title, String body) {
        return message(List.of(Container.of(TextDisplay.of("## " + title + "\n" + body))));
    }

    public static MessageCreateData longText(String title, String body) {
        var container = Container.of(TextDisplay.of("## " + title));
        for (int offset = 0; offset < body.length(); offset += 3500) {
            container = append(container, TextDisplay.of(body.substring(offset, Math.min(body.length(), offset + 3500))));
        }
        return message(List.of(container));
    }

    public static MessageCreateData message(List<? extends MessageTopLevelComponent> components) {
        return new MessageCreateBuilder().useComponentsV2().setComponents(components)
                .setAllowedMentions(List.of()).setSuppressedNotifications(true).build();
    }

    public static MessageEditData edit(MessageCreateData data) {
        return new MessageEditBuilder().useComponentsV2().setComponents(data.getComponents())
                .setAllowedMentions(List.of()).build();
    }

    public static String safe(String text) {
        return text.replace("@", "＠").replace("`", "ˋ");
    }
}
