package com.sumirelabs.kita.app;

import com.sumirelabs.kita.basic.*;
import com.sumirelabs.kita.discord.*;
import com.sumirelabs.kita.levels.*;
import com.sumirelabs.kita.logshare.*;
import com.sumirelabs.kita.music.*;
import com.sumirelabs.kita.previews.*;
import com.sumirelabs.kita.settings.*;
import com.sumirelabs.kita.stay.*;
import com.sumirelabs.kita.tickets.*;
import com.sumirelabs.kita.translation.*;
import com.sumirelabs.kita.earthquake.*;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.event.domain.interaction.*;
import discord4j.core.event.domain.lifecycle.DisconnectEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;

public final class FeatureWiring implements AutoCloseable {
    private final Disposable.Composite subscriptions = Disposables.composite();
    private final List<Command> commands;
    private final EarthquakeService earthquakes;
    public FeatureWiring(GatewayDiscordClient client, KitaConfig config, SettingsRepository settings, TicketRepository tickets,
                         MusicService music, StayService stay, String version, LogShareRepository logs, LevelsService levels, VoiceXpListener voiceXp,
                         EarthquakeRepository earthquakeRepository) {
        var feed = new P2pEarthquakeProvider();
        earthquakes = new EarthquakeService(client, settings, earthquakeRepository, feed);
        var earthquakeSettings = new EarthquakeSettingsListener(settings, feed);
        subscriptions.add(Events.listen(client, ButtonInteractionEvent.class, earthquakeSettings::button));
        subscriptions.add(Events.listen(client, SelectMenuInteractionEvent.class, earthquakeSettings::select));
        subscriptions.add(Events.listen(client, ModalSubmitInteractionEvent.class, earthquakeSettings::modal));
        var service = new TicketService(settings, tickets, new TicketTranscript(Path.of(config.dataDirectory()).resolve("transcripts")), client);
        var all = new ArrayList<Command>();
        all.add(new PingCommand()); all.add(new AvatarCommand()); all.add(new FastFetchCommand(version));
        all.add(new SettingsCommand(settings)); all.add(new TicketCommand(settings)); all.add(new StayCommand(stay));
        all.add(new QuakeCommand(feed));
        var images = new ProfileImages();
        all.add(new LevelsCommand(levels, images, "profile")); all.add(new LevelsCommand(levels, images, "leaderboard"));
        var chat = new ChatXpListener(levels); var board = new LeaderboardListener(levels);
        var levelSettings = new LevelsSettingsListener(settings, levels); var modals = levelSettings.modals();
        var stayListener = new StayListener(stay); var log = new LogShareListener(settings, logs, version);
        var ticket = new TicketListener(settings, service); var panel = new SettingsListener(settings, service::publish);
        var ytDlp = new YtDlpProvider(config.previews().ytDlp());
        var previews = new PreviewListener(settings, Map.of("x", new FxTwitterProvider(), "reddit", new RedditProvider(),
                "tiktok", new TikTokProvider(ytDlp), "twitch", ytDlp, "instagram", new InstagramProvider(ytDlp), "youtube", ytDlp));
        var translation = new FlagTranslationListener(settings,
                new TranslationService(config.deepl().key(), config.deepl().maxCharacters()), config.deepl().flagLanguages());
        subscriptions.add(Events.listen(client, MessageCreateEvent.class, chat::message));
        subscriptions.add(Events.listen(client, MessageCreateEvent.class, previews::message));
        subscriptions.add(Events.listen(client, MessageCreateEvent.class, log::message));
        subscriptions.add(Events.listen(client, ReactionAddEvent.class, translation::reaction));
        subscriptions.add(Events.listen(client, ReactionAddEvent.class, log::reaction));
        subscriptions.add(Events.listen(client, MessageInteractionEvent.class, log::context));
        subscriptions.add(Events.listen(client, ButtonInteractionEvent.class, log::button));
        subscriptions.add(Events.listen(client, ButtonInteractionEvent.class, ticket::button));
        subscriptions.add(Events.listen(client, ModalSubmitInteractionEvent.class, ticket::modal));
        subscriptions.add(Events.listen(client, ButtonInteractionEvent.class, stayListener::button));
        subscriptions.add(Events.listen(client, ModalSubmitInteractionEvent.class, stayListener::modal));
        subscriptions.add(Events.listen(client, ButtonInteractionEvent.class, panel::button));
        subscriptions.add(Events.listen(client, SelectMenuInteractionEvent.class, panel::select));
        subscriptions.add(Events.listen(client, ModalSubmitInteractionEvent.class, panel::modal));
        subscriptions.add(Events.listen(client, ButtonInteractionEvent.class, levelSettings::button));
        subscriptions.add(Events.listen(client, SelectMenuInteractionEvent.class, levelSettings::select));
        subscriptions.add(Events.listen(client, ModalSubmitInteractionEvent.class, modals::modal));
        subscriptions.add(Events.listen(client, ComponentInteractionEvent.class, board::component));
        subscriptions.add(Events.listen(client, VoiceStateUpdateEvent.class, voiceXp::voice));
        subscriptions.add(Events.listen(client, DisconnectEvent.class, event -> { voiceXp.reset(); return Mono.empty(); }));
        subscriptions.add(Events.listen(client, GuildDeleteEvent.class, event -> {
            if (event.isUnavailable()) return Mono.empty();
            long guild = event.getGuildId().asLong(); stay.forget(guild); voiceXp.forget(guild);
            return music == null ? Mono.empty() : music.forget(guild);
        }));
        if (music != null) {
            for (var name : new String[]{"play", "stop", "skip", "player"}) all.add(new MusicCommand(music, name));
            var listener = new MusicListener(music);
            subscriptions.add(Events.listen(client, ComponentInteractionEvent.class, listener::component));
        }
        commands = List.copyOf(all);
        var router = new CommandRouter(commands);
        subscriptions.add(Events.listen(client, ChatInputInteractionEvent.class, router::slash));
        subscriptions.add(Events.listen(client, MessageCreateEvent.class, router::prefix));
    }
    public List<Command> commands() { return commands; }
    public void start() { earthquakes.start(); }
    @Override public void close() { earthquakes.close(); subscriptions.dispose(); }
}
