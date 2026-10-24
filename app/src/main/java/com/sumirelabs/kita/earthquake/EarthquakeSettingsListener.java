package com.sumirelabs.kita.earthquake;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.Forms;
import com.sumirelabs.kita.discord.InteractionContext;
import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.component.Label;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.rest.util.Permission;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class EarthquakeSettingsListener {
    private final SettingsRepository repository;
    private final EarthquakeProvider provider;
    public EarthquakeSettingsListener(SettingsRepository repository, EarthquakeProvider provider) { this.repository = repository; this.provider = provider; }
    public Mono<Void> select(SelectMenuInteractionEvent event) {
        if (!event.getCustomId().startsWith("earthquake-settings:")) return Mono.empty();
        var context = new InteractionContext(event); var parts = event.getCustomId().split(":");
        if (!owner(context, parts)) return denied(context);
        var values = event.getValues(); String action = parts[2];
        if (action.equals("page")) return update(context, values.getFirst(), old -> Map.of(), Mono.empty());
        if (action.equals("channels")) {
            Mono<Void> validation = context.member().flatMap(member -> Flux.fromIterable(values)
                    .concatMap(value -> event.getClient().getChannelById(discord4j.common.util.Snowflake.of(value)).ofType(TextChannel.class)
                            .filter(channel -> channel.getGuildId().asLong() == context.guildId())
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("このサーバーのテキストチャンネルを選択してください。")))
                            .flatMap(channel -> Access.channel(member, channel, Permission.VIEW_CHANNEL)
                                    .then(Access.member(event.getClient(), context.guildId(), event.getClient().getSelfId().asLong()))
                                    .flatMap(bot -> Access.channel(bot, channel, Permission.VIEW_CHANNEL, Permission.SEND_MESSAGES)))).then());
            return update(context, "general", old -> Map.of("earthquake.channels", String.join(",", values)), validation);
        }
        if (action.equals("role")) {
            Mono<Void> validation = values.isEmpty() ? Mono.empty() : event.getClient().getGuildById(Access.id(context.guildId()))
                    .flatMap(guild -> guild.getRoleById(discord4j.common.util.Snowflake.of(values.getFirst())))
                    .filter(role -> !role.isManaged() && role.getId().asLong() != context.guildId())
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("通常のロールを選択してください。"))).then();
            return update(context, "delivery", old -> Map.of("earthquake.role", values.isEmpty() ? "" : values.getFirst()), validation);
        }
        if (action.equals("kinds") && Set.of("EARTHQUAKE", "EEW_WARNING", "TSUNAMI").containsAll(values)) {
            return update(context, "general", old -> Map.of("earthquake.kinds", String.join(",", values)), Mono.empty());
        }
        String value = values.getFirst();
        if (action.equals("minimum") && SeismicIntensity.SCALES.stream().anyMatch(scale -> String.valueOf(scale).equals(value))) {
            return update(context, "filters", old -> Map.of("earthquake.minimum", value), Mono.empty());
        }
        if (action.equals("sound") && Set.of("normal", "silent").contains(value)
                || action.equals("updates") && Set.of("new", "edit", "compact").contains(value)) {
            return update(context, "delivery", old -> Map.of("earthquake." + action, value), Mono.empty());
        }
        return Mono.empty();
    }
    public Mono<Void> button(ButtonInteractionEvent event) {
        if (!event.getCustomId().startsWith("earthquake-settings:")) return Mono.empty();
        var context = new InteractionContext(event); var parts = event.getCustomId().split(":");
        if (!owner(context, parts)) return denied(context);
        if (parts[2].equals("toggle")) return update(context, "general", old -> Map.of("earthquake.enabled",
                String.valueOf(!old.enabled("earthquake.enabled"))), Mono.empty());
        if (parts[2].equals("preview")) return context.defer(true).then(authorize(context)).then(context.followup(EarthquakeMessages.preview(), true))
                .onErrorResume(error -> context.followup(Ui.text("Seismo", errorMessage(error)), true));
        if (!parts[2].equals("regions")) return Mono.empty();
        return authorize(context).then(repository.get(context.guildId())).flatMap(settings -> event.presentModal(Forms.modal(
                "earthquake-regions:" + context.userId(), "対象地域のキーワード",
                Label.of("地震・EEWの地域（空欄で全国、カンマ区切り）", TextInput.paragraph("regions", 0, 500).required(false)
                        .prefilled(settings.value("earthquake.regions", "")).placeholder("例：東京都,千葉県,北海道")),
                Label.of("津波予報区（空欄で全国、カンマ区切り）", TextInput.paragraph("tsunamiRegions", 0, 500).required(false)
                        .prefilled(settings.value("earthquake.tsunamiRegions", "")).placeholder("例：千葉県,伊豆諸島")))))
                .onErrorResume(error -> context.privateReply(Ui.text("Seismo", errorMessage(error))));
    }
    public Mono<Void> modal(ModalSubmitInteractionEvent event) {
        if (!event.getCustomId().startsWith("earthquake-regions:")) return Mono.empty();
        var context = new InteractionContext(event); var parts = event.getCustomId().split(":");
        if (!owner(context, parts)) return denied(context);
        return context.defer(true).then(authorize(context)).then(Mono.defer(() -> repository.update(context.guildId(), Map.of(
                "earthquake.regions", clean(optional(event, "regions")), "earthquake.tsunamiRegions", clean(optional(event, "tsunamiRegions"))))))
                .flatMap(updated -> context.followup(EarthquakeSettings.render(updated, context.userId(), "filters", provider.status()), true))
                .onErrorResume(error -> context.followup(Ui.text("Seismo", errorMessage(error)), true));
    }
    private Mono<Void> update(InteractionContext context, String page, Function<GuildSettings, Map<String, String>> changes, Mono<Void> validation) {
        return context.deferEdit().then(authorize(context)).then(validation).then(repository.modify(context.guildId(), changes))
                .flatMap(updated -> context.edit(EarthquakeSettings.render(updated, context.userId(), page, provider.status())))
                .onErrorResume(error -> context.followup(Ui.text("Seismo", errorMessage(error)), true));
    }
    private static Mono<Void> authorize(InteractionContext context) {
        return context.member().flatMap(member -> Access.require(member, Permission.MANAGE_GUILD));
    }
    private static String optional(ModalSubmitInteractionEvent event, String id) {
        return event.getComponents(TextInput.class).stream().filter(input -> input.getCustomId().equals(id))
                .findFirst().flatMap(TextInput::getValue).orElse("");
    }
    static String clean(String value) {
        var keywords = EarthquakeConfig.keywords(value);
        if (keywords.stream().anyMatch(keyword -> keyword.length() > 40)) throw new IllegalArgumentException("地域名は1件40文字以内にしてください。");
        return String.join(",", keywords);
    }
    private static boolean owner(InteractionContext context, String[] parts) {
        return context.event().getInteraction().getGuildId().isPresent() && parts.length >= 2 && context.owner(parts[1]);
    }
    private static Mono<Void> denied(InteractionContext context) { return context.privateReply(Ui.text("Seismo", "この設定を操作する権限がありません。")); }
    private static String errorMessage(Throwable error) { return error instanceof IllegalArgumentException ? error.getMessage() : "設定を保存できませんでした。権限と接続を確認してください。"; }
}