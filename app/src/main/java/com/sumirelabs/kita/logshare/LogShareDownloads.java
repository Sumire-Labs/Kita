package com.sumirelabs.kita.logshare;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.WorkExecutor;
import com.sumirelabs.kita.settings.SettingsRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Semaphore;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.filedisplay.FileDisplay;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

final class LogShareDownloads {
    private static final String PREFIX = "logshare:download:";
    private final SettingsRepository settings;
    private final WorkExecutor worker;
    private final MclogsGateway gateway;
    private final Semaphore capacity = new Semaphore(4);
    LogShareDownloads(SettingsRepository settings, WorkExecutor worker, String version) {
        this.settings = settings; this.worker = worker; gateway = new MclogsGateway(version);
    }
    static String buttonId(String filename, String url) {
        if (!url.matches("https://mclo\\.gs/[A-Za-z0-9]{1,32}")) throw new IllegalArgumentException("ログURLが不正です。");
        String safe = filename.replaceAll("[\\\\/\\p{Cntrl}]", "_");
        if (!LogDetector.supported(safe)) safe += ".log";
        String id = url.substring(url.lastIndexOf('/') + 1);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(safe.getBytes(StandardCharsets.UTF_8));
        if (safe.isBlank() || PREFIX.length() + id.length() + 1 + encoded.length() > 100) {
            encoded = Base64.getUrlEncoder().withoutPadding().encodeToString("log.log".getBytes(StandardCharsets.UTF_8));
        }
        return PREFIX + id + ":" + encoded;
    }
    static String filename(String componentId) {
        String name = new String(Base64.getUrlDecoder().decode(componentId.substring(componentId.lastIndexOf(':') + 1)), StandardCharsets.UTF_8);
        if (name.isBlank() || name.getBytes(StandardCharsets.UTF_8).length > 64 || name.matches(".*[\\\\/\\p{Cntrl}].*")) {
            throw new IllegalArgumentException("ファイル名が不正です。");
        }
        return name;
    }
    static MessageCreateData file(String filename, byte[] content) {
        return MessageCreateBuilder.from(Ui.message(List.of(Container.of(FileDisplay.fromFileName(filename)))))
                .addFiles(FileUpload.fromData(content, filename)).build();
    }
    void handle(ButtonInteractionEvent event) {
        if (!event.isFromGuild() || !event.getComponentId().startsWith(PREFIX)) return;
        event.deferReply(true).queue(hook -> {
            if (!worker.submit(() -> {
                if (!capacity.tryAcquire()) { hook.sendMessage(Ui.text("LogShare", "現在混み合っています。")).queue(); return; }
                boolean queued = false;
                try {
                    require(event);
                    String[] parts = event.getComponentId().split(":", 4);
                    byte[] content = gateway.download(parts[2]);
                    require(event);
                    hook.sendMessage(file(filename(event.getComponentId()), content)).queue(ignored -> capacity.release(), error -> {
                        capacity.release();
                        hook.sendMessage(Ui.text("LogShare", "ファイルを送信できませんでした。もう一度お試しください。"))
                                .queue(ignored -> {}, ignored -> {});
                    });
                    queued = true;
                } catch (Exception error) {
                    hook.sendMessage(Ui.text("LogShare", LogShareMessages.error(error,
                            "ログを取得できませんでした。リンク先の保存期限と接続を確認してください。"))).queue();
                } finally { if (!queued) capacity.release(); }
            })) hook.sendMessage(Ui.text("LogShare", "現在混み合っています。")).queue();
        });
    }
    private void require(ButtonInteractionEvent event) {
        var config = settings.get(event.getGuild().getIdLong());
        long parent = event.getChannel() instanceof ThreadChannel thread ? thread.getParentChannel().getIdLong() : 0;
        if (!config.enabled("logshare.enabled") || LogSharePolicy.excluded(config, event.getChannel().getIdLong(), parent)) {
            throw new IllegalArgumentException("このチャンネルではLogShareは利用できません。");
        }
        var member = event.getGuild().retrieveMemberById(event.getUser().getIdLong()).complete();
        if (!member.hasPermission(event.getGuildChannel(), Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY)) {
            throw new IllegalArgumentException("このチャンネルの閲覧権限が必要です。");
        }
    }
}
