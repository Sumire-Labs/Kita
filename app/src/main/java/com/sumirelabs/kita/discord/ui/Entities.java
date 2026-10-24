package com.sumirelabs.kita.discord.ui;

import discord4j.common.util.Snowflake;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.entity.channel.Channel;
import java.util.Collection;
import java.util.List;

public final class Entities {
    public enum Target { CHANNEL, ROLE }
    private final String id;
    private final Target target;
    private List<Channel.Type> types = List.of();
    private List<SelectMenu.DefaultValue> defaults = List.of();
    private String placeholder;
    private int minimum = 1, maximum = 1;
    private Entities(String id, Target target) { this.id = id; this.target = target; }
    public static Entities create(String id, Target target) { return new Entities(id, target); }
    public Entities setChannelTypes(Channel.Type... types) { this.types = List.of(types); return this; }
    public Entities setDefaultValues(SelectMenu.DefaultValue... values) { defaults = List.of(values); return this; }
    public Entities setDefaultValues(Collection<SelectMenu.DefaultValue> values) { defaults = List.copyOf(values); return this; }
    public Entities setPlaceholder(String text) { placeholder = text; return this; }
    public Entities setMinValues(int value) { minimum = value; return this; }
    public Entities setMaxValues(int value) { maximum = value; return this; }
    public SelectMenu build() {
        var menu = (target == Target.ROLE ? SelectMenu.ofRole(id, defaults) : SelectMenu.ofChannel(id, defaults, types))
                .withMinValues(minimum).withMaxValues(maximum);
        return placeholder == null ? menu : menu.withPlaceholder(placeholder);
    }
    public static SelectMenu.DefaultValue channel(String id) { return SelectMenu.DefaultValue.of(Snowflake.of(id), SelectMenu.DefaultValue.Type.CHANNEL); }
    public static SelectMenu.DefaultValue channel(long id) { return channel(Long.toUnsignedString(id)); }
    public static SelectMenu.DefaultValue role(String id) { return SelectMenu.DefaultValue.of(Snowflake.of(id), SelectMenu.DefaultValue.Type.ROLE); }
}
