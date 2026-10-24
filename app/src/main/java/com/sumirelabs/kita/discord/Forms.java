package com.sumirelabs.kita.discord;

import discord4j.core.object.component.Label;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.component.TopLevelModalComponent;
import discord4j.core.spec.InteractionPresentModalSpec;
import java.util.List;

public final class Forms {
    private Forms() {}
    public static Label input(String id, String label, int max) { return Label.of(label, TextInput.small(id, 1, max)); }
    public static Label input(String id, String label, int max, String value) { return Label.of(label, TextInput.small(id, 1, max).prefilled(value)); }
    public static Label paragraph(String id, String label, int max) { return Label.of(label, TextInput.paragraph(id, 1, max)); }
    public static InteractionPresentModalSpec modal(String id, String title, TopLevelModalComponent... fields) {
        return InteractionPresentModalSpec.builder().customId(id).title(title).components(List.of(fields)).build();
    }
}
