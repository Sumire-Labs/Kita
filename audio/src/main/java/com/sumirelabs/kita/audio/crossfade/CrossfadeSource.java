package com.sumirelabs.kita.audio.crossfade;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import java.io.*;
import org.springframework.stereotype.Component;

@Component
public final class CrossfadeSource implements AudioSourceManager {
    private final DefaultAudioPlayerManager pcm = new DefaultAudioPlayerManager();
    public CrossfadeSource() {
        pcm.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_LE);
        pcm.getConfiguration().setResamplingQuality(com.sedmelluq.discord.lavaplayer.player.AudioConfiguration.ResamplingQuality.HIGH);
        pcm.setTrackStuckThreshold(60_000);
        pcm.setFrameBufferDuration(1000);
        ((java.util.concurrent.ThreadPoolExecutor) pcm.getExecutor()).setMaximumPoolSize(64);
    }
    AudioPlayerManager pcm() { return pcm; }
    @Override public String getSourceName() { return "kita-crossfade"; }
    @Override public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) { return null; }
    @Override public boolean isTrackEncodable(AudioTrack track) { return false; } // Live composite state is node-local.
    @Override public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        var mixed = (CrossfadeTrack) track; output.writeUTF(mixed.encoded); output.writeUTF(mixed.token); output.writeInt(mixed.seconds);
    }
    @Override public AudioTrack decodeTrack(AudioTrackInfo info, DataInput input) throws IOException {
        throw new IOException("Crossfade sessions cannot be resumed as a single encoded track");
    }
    @Override public void shutdown() { pcm.shutdown(); }
}
