package cn.hellocsc.storage;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cn.hellocsc.model.ShareContent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class MemoryTextStorage {
    // 使用Caffeine缓存，24小时过期
    private final Cache<String, ShareContent> textCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(1000)
            .build();

    public void save(ShareContent content) {
        textCache.put(content.getShareId(), content);
    }

    public Optional<ShareContent> get(String shareId) {
        return Optional.ofNullable(textCache.getIfPresent(shareId));
    }

    public void invalidate(String shareId) {
        textCache.invalidate(shareId);
    }
}