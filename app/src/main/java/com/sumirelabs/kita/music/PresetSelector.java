package com.sumirelabs.kita.music;

import com.sumirelabs.kita.common.PresetFiles.Preset;
import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.emoji.Emoji;

public final class PresetSelector {
    static final int PAGE_SIZE = 22;
    private PresetSelector() {}
    static StringSelectMenu menu(List<Preset> presets, String selected, int page) {
        int lastPage = Math.max(0, (presets.size() - 1) / PAGE_SIZE);
        page = Math.clamp(page, 0, lastPage);
        var menu = StringSelectMenu.create("music:preset")
                .setPlaceholder("プリセット（" + (page + 1) + "/" + (lastPage + 1) + "）").addOption("無効", "off");
        var visible = presets.stream().skip((long) page * PAGE_SIZE).limit(PAGE_SIZE).toList();
        visible.forEach(p -> menu.addOptions(option(p)));
        if (page > 0) menu.addOptions(SelectOption.of("前のプリセット一覧", "page:prev").withEmoji(Emoji.fromUnicode("◀️")));
        if (page < lastPage) menu.addOptions(SelectOption.of("次のプリセット一覧", "page:next").withEmoji(Emoji.fromUnicode("▶️")));
        if (selected.equals("off") || visible.stream().anyMatch(p -> p.id().equals(selected))) menu.setDefaultValues(selected);
        return menu.build();
    }
    public static SelectOption option(Preset preset) {
        var option = SelectOption.of(shorten(oneLine(preset.name()), 100), preset.id());
        String description = oneLine(preset.description());
        return description.isBlank() ? option : option.withDescription(shorten(description, 100));
    }

    public static String details(List<Preset> presets, String id) {
        if (id.equals("off")) return "HRIR: 無効";
        return presets.stream().filter(p -> p.id().equals(id)).findFirst().map(p ->
                "HRIR: **" + Ui.safe(shorten(oneLine(p.name()), 100)) + "**"
                        + (p.description().isBlank() ? "" : "\n" + Ui.safe(shorten(p.description(), 1000))))
                .orElse("HRIR: プリセットが見つかりません");
    }

    private static String oneLine(String value) { return value.replaceAll("\\s+", " ").strip(); }
    private static String shorten(String value, int limit) {
        if (value.length() <= limit) return value;
        int end = limit - 1;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…";
    }
}
