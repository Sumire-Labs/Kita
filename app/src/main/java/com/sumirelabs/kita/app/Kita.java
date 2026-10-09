package com.sumirelabs.kita.app;

import com.sumirelabs.kita.discord.WorkExecutor;
import com.sumirelabs.kita.music.MusicService;
import com.sumirelabs.kita.music.PlayerRefresh;
import com.sumirelabs.kita.storage.Database;
import com.sumirelabs.kita.storage.JdbcSettingsRepository;
import com.sumirelabs.kita.storage.JdbcTicketRepository;
import com.sumirelabs.kita.storage.JdbcLogShareRepository;
import com.sumirelabs.kita.tickets.TicketRecovery;
import dev.arbjerg.lavalink.libraries.jda.JDAVoiceUpdateListener;
import dev.arbjerg.lavalink.client.Helpers;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.LoggerFactory;

public final class Kita implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final WorkExecutor worker = new WorkExecutor();
    private Database database;
    private MusicService music;
    private ShardManager shards;
    private PlayerRefresh refresh;
    private HealthServer health;
    private TicketRecovery ticketRecovery;
    private StatusPresence presence;

    public static void main(String[] arguments) throws Exception {
        boolean check = arguments.length > 0 && arguments[0].equals("--check-config");
        var path = Path.of(arguments.length > (check ? 1 : 0) ? arguments[check ? 1 : 0] : "config/kita.yml");
        var config = ConfigLoader.load(path);
        if (check) { System.out.println("Kita configuration is valid (credentials were not contacted)."); return; }
        var application = new Kita();
        try {
            application.start(config);
            Runtime.getRuntime().addShutdownHook(new Thread(application::close, "kita-shutdown"));
        } catch (Exception error) { application.close(); throw error; }
    }

    private void start(KitaConfig config) throws Exception {
        var buildVersion = version();
        presence = new StatusPresence(buildVersion);
        var db = config.database();
        database = new Database(db.url(), db.user(), db.password());
        if (config.music().enabled()) {
            music = new MusicService(Helpers.getUserIdFromToken(config.bot().token()), config.music().uri(),
                    config.music().password(), Path.of(config.music().presetsDirectory()), worker, config.music().defaultPreset());
        }
        var builder = DefaultShardManagerBuilder.createLight(config.bot().token(), GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_VOICE_STATES)
                .enableCache(CacheFlag.VOICE_STATE)
                .setMemberCachePolicy(MemberCachePolicy.VOICE).setEnableShutdownHook(false)
                .setShardsTotal(config.bot().shards() == 0 ? -1 : config.bot().shards())
                .setActivity(presence.current());
        if (music != null) builder.setVoiceDispatchInterceptor(new JDAVoiceUpdateListener(music.client()));
        builder.addEventListeners(FeatureWiring.listeners(config, new JdbcSettingsRepository(database.source()),
                new JdbcTicketRepository(database.source()), worker, music,
                id -> shards == null ? null : shards.getGuildById(id), buildVersion, new JdbcLogShareRepository(database.source())));
        shards = builder.build();
        presence.start(shards);
        ticketRecovery = new TicketRecovery(new JdbcTicketRepository(database.source()), shards::getGuildById, worker);
        if (music != null) refresh = new PlayerRefresh(music, shards::getGuildById, worker);
        health = new HealthServer(() -> shards.getShards().size() == shards.getShardsTotal()
                && !shards.getShards().isEmpty()
                && shards.getShards().stream().allMatch(shard -> shard.getStatus() == JDA.Status.CONNECTED));
        LoggerFactory.getLogger(Kita.class).info("Kita {} started with {} shards", buildVersion, shards.getShardsTotal());
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
        if (health != null) health.close();
        if (presence != null) presence.close();
        if (refresh != null) refresh.close();
        if (ticketRecovery != null) ticketRecovery.close();
        worker.close();
        if (music != null) music.close();
        if (shards != null) shards.shutdown();
        if (database != null) database.close();
    }
}
