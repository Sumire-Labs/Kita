package com.sumirelabs.kita.levels;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Member;

public final class ProfileImages {
    private record Key(ProfileCardRenderer.Card card, String avatar) {}
    private final Cache<String, byte[]> avatars = Caffeine.newBuilder().maximumWeight(32 * 1024 * 1024)
            .weigher((String key, byte[] value) -> value.length).expireAfterWrite(Duration.ofMinutes(15)).build();
    private final Cache<Key, byte[]> cards = Caffeine.newBuilder().maximumWeight(32 * 1024 * 1024)
            .weigher((Key key, byte[] value) -> value.length).expireAfterWrite(Duration.ofSeconds(60)).build();
    private final Semaphore renders = new Semaphore(2);
    public byte[] profile(Member member, LevelsRepository.Profile profile) throws Exception {
        String url = member.getEffectiveAvatarUrl() + "?size=256";
        var card = new ProfileCardRenderer.Card(member.getIdLong(), member.getEffectiveName(), profile.xp(), profile.rank());
        var key = new Key(card, url);
        var cached = cards.getIfPresent(key); if (cached != null) return cached;
        if (!renders.tryAcquire()) throw new IllegalArgumentException("プロフィール画像を生成中です。少し待ってください。");
        try {
            byte[] avatar = avatars.getIfPresent(url);
            if (avatar == null) {
                try (var stream = new net.dv8tion.jda.api.utils.ImageProxy(url).download().get(10, TimeUnit.SECONDS)) {
                    avatar = stream.readNBytes(1024 * 1024 + 1);
                    if (avatar.length > 1024 * 1024) avatar = new byte[0];
                    if (avatar.length != 0) avatars.put(url, avatar);
                } catch (Exception ignored) { avatar = new byte[0]; }
            }
            byte[] result = new ProfileCardRenderer().render(card, avatar); cards.put(key, result); return result;
        } finally { renders.release(); }
    }
}
