package com.sumirelabs.kita.app;

import com.sumirelabs.kita.discord.ReactiveWork;
import com.sumirelabs.kita.levels.LevelsService;
import com.sumirelabs.kita.levels.VoiceXpListener;
import com.sumirelabs.kita.music.MusicService;
import com.sumirelabs.kita.music.PlayerRefresh;
import com.sumirelabs.kita.stay.StayService;
import com.sumirelabs.kita.storage.*;
import com.sumirelabs.kita.tickets.TicketRecovery;
import dev.arbjerg.lavalink.client.Helpers;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.shard.ShardingStrategy;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class Kita implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean ready = new AtomicBoolean();
    private Database database;
    private MusicService music;
    private GatewayDiscordClient gateway;
    private PlayerRefresh refresh;
    private StayService stay;
    private HealthServer health;
    private TicketRecovery ticketRecovery;
    private StatusPresence presence;
    private VoiceXpListener voiceXp;
    private LevelsService levels;
    private FeatureWiring features;
    private Disposable healthPoll;

    public static void main(String[] arguments) throws Exception {
        boolean check = arguments.length > 0 && arguments[0].equals("--check-config");
        var path = Path.of(arguments.length > (check ? 1 : 0) ? arguments[check ? 1 : 0] : "config/kita.yml");
        var config = ConfigLoader.load(path);
        if (check) { System.out.println("Kita configuration is valid (credentials were not contacted)."); return; }
        var application = new Kita();
        Runtime.getRuntime().addShutdownHook(new Thread(application::close, "kita-shutdown"));
        try {
            application.start(config);
            application.gateway.onDisconnect().block();
        } finally { application.close(); }
    }

    private void start(KitaConfig config) throws Exception {
        var version = version(); presence = new StatusPresence(version);
        var db = config.database(); database = new Database(db.url(), db.user(), db.password());
        database.migrate().block(Duration.ofMinutes(2));
        var settings = new R2dbcSettingsRepository(database.source());
        var tickets = new R2dbcTicketRepository(database.source());
        levels = new LevelsService(settings, new R2dbcLevelsRepository(database.source()));
        if (config.music().enabled()) music = new MusicService(Helpers.getUserIdFromToken(config.bot().token()),
                config.music().uri(), config.music().password(), Path.of(config.music().presetsDirectory()), config.music().defaultPreset());
        var initial = ReactiveWork.io(presence::current).block(Duration.ofSeconds(20));
        gateway = DiscordClient.create(config.bot().token()).gateway()
                .setEnabledIntents(IntentSet.of(Intent.GUILDS, Intent.GUILD_MESSAGES, Intent.MESSAGE_CONTENT,
                        Intent.GUILD_MESSAGE_REACTIONS, Intent.GUILD_VOICE_STATES))
                .setSharding(config.bot().shards() == 0 ? ShardingStrategy.recommended() : ShardingStrategy.fixed(config.bot().shards()))
                .setInitialPresence(shard -> ClientPresence.online(initial)).login().block(Duration.ofMinutes(3));
        if (gateway == null) throw new IllegalStateException("Discord login did not complete");
        if (music != null) music.installVoice(gateway);
        stay = new StayService(gateway, music); voiceXp = new VoiceXpListener(levels, gateway);
        features = new FeatureWiring(gateway, config, settings, tickets, music, stay, version,
                new R2dbcLogShareRepository(database.source()), levels, voiceXp, new R2dbcEarthquakeRepository(database.source()));
        CommandRegistration.register(gateway, features.commands(), config.bot().developmentGuildId()).block(Duration.ofMinutes(1));
        features.start();
        presence.start(gateway); ticketRecovery = new TicketRecovery(tickets, gateway);
        if (music != null) refresh = new PlayerRefresh(music, gateway);
        healthPoll = Flux.interval(Duration.ZERO, Duration.ofSeconds(2)).concatMap(tick ->
                Flux.range(0, gateway.getGatewayClientGroup().getShardCount()).flatMap(shard ->
                        gateway.getGatewayClient(shard).map(client -> client.isConnected()).orElse(Mono.just(false))).all(Boolean::booleanValue))
                .subscribe(ready::set, error -> ready.set(false));
        health = new HealthServer(ready::get);
        LoggerFactory.getLogger(Kita.class).info("Kita {} started with {} shards", version, gateway.getGatewayClientGroup().getShardCount());
    }
    static String version() throws Exception {
        var properties = new Properties();
        try (var stream = Kita.class.getResourceAsStream("/kita-build.properties")) {
            if (stream == null) throw new IllegalStateException("Build metadata missing");
            properties.load(stream);
        }
        return properties.getProperty("version");
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ready.set(false);
        if (health != null) health.close();
        if (healthPoll != null) healthPoll.dispose();
        if (features != null) features.close();
        if (presence != null) presence.close();
        if (refresh != null) refresh.close();
        if (stay != null) stay.close();
        if (voiceXp != null) voiceXp.close();
        if (levels != null) levels.close();
        if (ticketRecovery != null) ticketRecovery.close();
        if (gateway != null) { try { gateway.logout().block(Duration.ofSeconds(15)); } catch (RuntimeException ignored) { } }
        if (music != null) music.close();
        if (database != null) { try { database.dispose().block(Duration.ofSeconds(15)); } catch (RuntimeException ignored) { } }
        ReactiveWork.close();
    }
}
