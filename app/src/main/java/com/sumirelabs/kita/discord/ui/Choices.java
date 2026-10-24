package com.sumirelabs.kita.discord.ui;

import discord4j.core.object.component.SelectMenu;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Small view-construction helper; the resulting component is Discord4J's native SelectMenu. */
public final class Choices {
    private final String id;
    private final List<SelectMenu.Option> options = new ArrayList<>();
    private Set<String> defaults = Set.of();
    private String placeholder;
    private int minimum = 1, maximum = 1;
    private Choices(String id) { this.id = id; }
    public static Choices create(String id) { return new Choices(id); }
    public Choices addOption(String label, String value) { options.add(SelectMenu.Option.of(label, value)); return this; }
    public Choices addOptions(SelectMenu.Option... entries) { options.addAll(List.of(entries)); return this; }
    public Choices addOptions(Collection<SelectMenu.Option> entries) { options.addAll(entries); return this; }
    public Choices setDefaultValues(String... values) { defaults = Set.of(values); return this; }
    public Choices setDefaultValues(Collection<String> values) { defaults = Set.copyOf(values); return this; }
    public Choices setPlaceholder(String text) { placeholder = text; return this; }
    public Choices setMinValues(int value) { minimum = value; return this; }
    public Choices setMaxValues(int value) { maximum = value; return this; }
    public SelectMenu build() {
        var menu = SelectMenu.of(id, options.stream().map(option -> option.withDefault(defaults.contains(option.getValue()))).toList())
                .withMinValues(minimum).withMaxValues(maximum);
        return placeholder == null ? menu : menu.withPlaceholder(placeholder);
    }
}
