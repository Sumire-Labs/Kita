package com.sumirelabs.kita.app;

import com.sumirelabs.kita.basic.AvatarCommand;
import com.sumirelabs.kita.basic.FastFetchCommand;
import com.sumirelabs.kita.basic.PingCommand;
import com.sumirelabs.kita.settings.SettingsRepository;
import com.sumirelabs.kita.tickets.TicketRepository;
import com.sumirelabs.kita.discord.Command;
import com.sumirelabs.kita.discord.CommandRouter;
import com.sumirelabs.kita.discord.WorkExecutor;
import com.sumirelabs.kita.music.MusicCommand;
import com.sumirelabs.kita.music.MusicListener;
import com.sumirelabs.kita.music.MusicService;
import com.sumirelabs.kita.logshare.LogShareRepository;
import com.sumirelabs.kita.logshare.LogShareListener;
import com.sumirelabs.kita.previews.FxTwitterProvider;
import com.sumirelabs.kita.previews.InstagramProvider;
import com.sumirelabs.kita.previews.TikTokProvider;
import com.sumirelabs.kita.previews.PreviewListener;
import com.sumirelabs.kita.previews.RedditProvider;
import com.sumirelabs.kita.previews.YtDlpProvider;
import com.sumirelabs.kita.settings.SettingsCommand;
import com.sumirelabs.kita.settings.SettingsListener;
import com.sumirelabs.kita.tickets.TicketCommand;
import com.sumirelabs.kita.tickets.TicketListener;
import com.sumirelabs.kita.tickets.TicketService;
import com.sumirelabs.kita.tickets.TicketTranscript;
import com.sumirelabs.kita.translation.FlagTranslationListener;
import com.sumirelabs.kita.translation.TranslationService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.LongFunction;
import net.dv8tion.jda.api.entities.Guild;

public final class FeatureWiring {
    private FeatureWiring() {}
    public static Object[] listeners(KitaConfig config, SettingsRepository settings, TicketRepository tickets,
                                     WorkExecutor worker, MusicService music, LongFunction<Guild> guilds, String version, LogShareRepository logs) {
        var service = new TicketService(settings, tickets,
                new TicketTranscript(Path.of(config.dataDirectory()).resolve("transcripts")), guilds);
        var commands = new ArrayList<Command>();
        commands.add(new PingCommand()); commands.add(new AvatarCommand()); commands.add(new FastFetchCommand(version));
        commands.add(new SettingsCommand(settings)); commands.add(new TicketCommand(settings));
        var listeners = new ArrayList<Object>();
        listeners.add(new LogShareListener(settings, logs, worker, version));
        if (music != null) {
            for (var name : new String[]{"play", "stop", "skip", "player"}) commands.add(new MusicCommand(music, name));
            listeners.add(new MusicListener(music, worker));
        }
        var ytDlp = new YtDlpProvider(config.previews().ytDlp());
        listeners.add(new PreviewListener(settings, worker, Map.of("x", new FxTwitterProvider(), "reddit", new RedditProvider(),
                "tiktok", new TikTokProvider(ytDlp), "twitch", ytDlp, "instagram", new InstagramProvider(ytDlp), "youtube", ytDlp)));
        listeners.add(new FlagTranslationListener(settings,
                new TranslationService(config.deepl().key(), config.deepl().maxCharacters()), worker, config.deepl().flagLanguages()));
        listeners.add(new TicketListener(settings, service, worker));
        listeners.add(new SettingsListener(settings, worker, service::publish));
        listeners.add(new CommandRouter(commands, worker));
        listeners.add(new CommandRegistration(commands, config.bot().developmentGuildId()));
        return listeners.toArray();
    }
}
