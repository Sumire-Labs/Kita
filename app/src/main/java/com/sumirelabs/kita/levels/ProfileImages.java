package com.sumirelabs.kita.levels;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sumirelabs.kita.discord.HttpBytes;
import com.sumirelabs.kita.discord.ReactiveWork;
import discord4j.core.object.entity.Member;
import java.time.Duration;
import reactor.core.publisher.Mono;

public final class ProfileImages {
    private record Key(ProfileCardRenderer.Card card, String avatar) {}
    private final Cache<String, byte[]> avatars = Caffeine.newBuilder().maximumWeight(32 * 1024 * 1024)
            .weigher((String key, byte[] value) -> value.length).expireAfterWrite(Duration.ofMinutes(15)).build();
    private final Cache<Key, byte[]> cards = Caffeine.newBuilder().maximumWeight(32 * 1024 * 1024)
            .weigher((Key key, byte[] value) -> value.length).expireAfterWrite(Duration.ofSeconds(60)).build();
    public Mono<byte[]> profile(Member member, LevelsRepository.Profile profile) {
        String base = member.getEffectiveAvatarUrl();
        String url = base + (base.contains("?") ? "&" : "?") + "size=256";
        var card = new ProfileCardRenderer.Card(member.getId().asLong(), member.getDisplayName(), profile.xp(), profile.rank());
        var key = new Key(card, url);
        return Mono.defer(() -> {
            var cached = cards.getIfPresent(key);
            if (cached != null) return Mono.just(cached);
            var avatar = avatars.getIfPresent(url);
            Mono<byte[]> bytes = avatar == null ? HttpBytes.get(url, 1024 * 1024).doOnNext(data -> avatars.put(url, data))
                    .onErrorReturn(new byte[0]) : Mono.just(avatar);
            return bytes.flatMap(data -> ReactiveWork.cpu(() -> new ProfileCardRenderer().render(card, data)))
                    .doOnNext(data -> cards.put(key, data));
        });
    }
}
