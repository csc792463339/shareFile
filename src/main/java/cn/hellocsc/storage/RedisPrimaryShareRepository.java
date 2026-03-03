package cn.hellocsc.storage;

import cn.hellocsc.model.AdminShareStats;
import cn.hellocsc.model.AdminSharesPageResponse;
import cn.hellocsc.model.ShareContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPrimaryShareRepository implements ShareRepository {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String KEY_META_PREFIX = "share:meta:";
    private static final String KEY_INDEX_CREATED = "share:index:created";
    private static final String KEY_STATS = "share:stats";
    private static final String KEY_DIRTY_VIEW = "share:dirty:view";
    private static final String KEY_RESERVED_PREFIX = "share:id:reserved:";

    private final StringRedisTemplate stringRedisTemplate;
    private final PersistentTextStorage backupStorage;

    @Value("${app.redis.fallback-enabled:true}")
    private boolean fallbackEnabled;

    private final ConcurrentHashMap<String, Long> localReservations = new ConcurrentHashMap<>();

    @Override
    public void saveShare(ShareContent share) {
        normalizeShare(share);
        backupStorage.save(share);

        if (isExpired(share)) {
            return;
        }

        try {
            String key = metaKey(share.getShareId());
            boolean existed = Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
            writeShareToRedis(share);
            if (!existed) {
                incrementStatsOnCreate(share);
            }
        } catch (Exception e) {
            onRedisFailure("写入分享失败，使用本地存储兜底", e);
        }
    }

    @Override
    public Optional<ShareContent> getShare(String shareId, boolean incrementViewCount) {
        try {
            Optional<ShareContent> fromRedis = getShareFromRedis(shareId, incrementViewCount);
            if (fromRedis.isPresent()) {
                return fromRedis;
            }
        } catch (Exception e) {
            onRedisFailure("读取分享失败，降级本地存储", e);
        }

        if (!fallbackEnabled) {
            return Optional.empty();
        }

        Optional<ShareContent> fromBackup = backupStorage.get(shareId);
        if (fromBackup.isEmpty()) {
            return Optional.empty();
        }

        ShareContent share = fromBackup.get();
        normalizeShare(share);
        if (isExpired(share)) {
            backupStorage.invalidate(shareId);
            return Optional.empty();
        }

        if (incrementViewCount) {
            share.setViewCount(share.getViewCount() + 1);
            backupStorage.save(share);
        }
        fillRemaining(share);

        // 本地命中后尝试回填 Redis（不影响主流程）
        try {
            writeShareToRedis(share);
        } catch (Exception e) {
            onRedisFailure("回填 Redis 失败，忽略", e);
        }

        return Optional.of(share);
    }

    @Override
    public void deleteShare(String shareId) {
        backupStorage.invalidate(shareId);
        try {
            Optional<ShareContent> existed = getShareFromRedis(shareId, false);
            stringRedisTemplate.delete(metaKey(shareId));
            stringRedisTemplate.opsForZSet().remove(KEY_INDEX_CREATED, shareId);
            stringRedisTemplate.opsForSet().remove(KEY_DIRTY_VIEW, shareId);

            if (existed.isPresent()) {
                decrementStatsOnDelete(existed.get());
            }
        } catch (Exception e) {
            onRedisFailure("删除分享时 Redis 不可用，本地删除已完成", e);
        }
    }

    @Override
    public boolean reserveShareId(String shareId, Duration ttl) {
        String reserveKey = KEY_RESERVED_PREFIX + shareId;
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(metaKey(shareId)))) {
                return false;
            }
            if (fallbackEnabled && backupStorage.get(shareId).isPresent()) {
                return false;
            }
            Boolean reserved = stringRedisTemplate.opsForValue()
                    .setIfAbsent(reserveKey, "1", ttl);
            return Boolean.TRUE.equals(reserved);
        } catch (Exception e) {
            onRedisFailure("分享码占位失败，使用本地占位降级", e);
            if (!fallbackEnabled) {
                return false;
            }

            long now = System.currentTimeMillis();
            localReservations.entrySet().removeIf(entry -> entry.getValue() <= now);
            if (exists(shareId)) {
                return false;
            }
            long expireAt = now + ttl.toMillis();
            return localReservations.putIfAbsent(shareId, expireAt) == null;
        }
    }

    @Override
    public boolean exists(String shareId) {
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(metaKey(shareId)))) {
                return true;
            }
        } catch (Exception e) {
            onRedisFailure("检查分享码存在性失败，降级本地", e);
        }
        return backupStorage.get(shareId).isPresent();
    }

    @Override
    public AdminSharesPageResponse queryShares(int page, int size, String type, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, size);
        try {
            List<String> ids = getAllShareIds(sort);
            List<ShareContent> filtered = new ArrayList<>();

            for (String id : ids) {
                Optional<ShareContent> shareOpt = getShareFromRedis(id, false);
                if (shareOpt.isEmpty()) {
                    stringRedisTemplate.opsForZSet().remove(KEY_INDEX_CREATED, id);
                    continue;
                }

                ShareContent share = shareOpt.get();
                if (matchesTypeFilter(share, type)) {
                    fillRemaining(share);
                    filtered.add(share);
                }
            }

            long totalItems = filtered.size();
            int totalPages = (int) Math.ceil(totalItems / (double) safeSize);
            int fromIndex = Math.min(safePage * safeSize, filtered.size());
            int toIndex = Math.min(fromIndex + safeSize, filtered.size());
            List<ShareContent> pageItems = filtered.subList(fromIndex, toIndex);

            return new AdminSharesPageResponse(
                    pageItems,
                    safePage,
                    safeSize,
                    totalItems,
                    totalPages,
                    queryStatsFromRedis(filtered)
            );
        } catch (Exception e) {
            onRedisFailure("查询管理分页失败，降级本地", e);
            return querySharesFromBackup(safePage, safeSize, type, sort);
        }
    }

    @Override
    public void flushDirtyViewCountsToBackup() {
        try {
            Set<String> dirtyIds = stringRedisTemplate.opsForSet().members(KEY_DIRTY_VIEW);
            if (dirtyIds == null || dirtyIds.isEmpty()) {
                return;
            }

            for (String id : dirtyIds) {
                Optional<ShareContent> shareOpt = getShareFromRedis(id, false);
                if (shareOpt.isPresent()) {
                    backupStorage.save(shareOpt.get());
                }
                stringRedisTemplate.opsForSet().remove(KEY_DIRTY_VIEW, id);
            }
        } catch (Exception e) {
            onRedisFailure("回刷访问计数失败", e);
        }
    }

    @Override
    public void bootstrapFromBackupIfEmpty() {
        try {
            Long existing = stringRedisTemplate.opsForZSet().zCard(KEY_INDEX_CREATED);
            if (existing != null && existing > 0) {
                return;
            }

            List<ShareContent> shares = backupStorage.getAllShares();
            if (shares.isEmpty()) {
                return;
            }

            long total = 0;
            long file = 0;
            long text = 0;
            long totalViews = 0;

            for (ShareContent share : shares) {
                normalizeShare(share);
                long remainingSeconds = computeRemainingSeconds(share);
                if (remainingSeconds <= 0) {
                    continue;
                }
                writeShareToRedis(share);
                total++;
                totalViews += Math.max(share.getViewCount(), 0);
                if (share.isFile()) {
                    file++;
                } else {
                    text++;
                }
            }

            Map<String, String> stats = new HashMap<>();
            stats.put("total", String.valueOf(total));
            stats.put("file", String.valueOf(file));
            stats.put("text", String.valueOf(text));
            stats.put("totalViews", String.valueOf(totalViews));
            stringRedisTemplate.opsForHash().putAll(KEY_STATS, stats);

            log.info("已从本地备份迁移 {} 条分享到 Redis", total);
        } catch (Exception e) {
            onRedisFailure("启动迁移到 Redis 失败，继续使用本地存储", e);
        }
    }

    private Optional<ShareContent> getShareFromRedis(String shareId, boolean incrementViewCount) {
        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(metaKey(shareId));
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }

        ShareContent share = fromHash(raw);
        normalizeShare(share);
        if (isExpired(share)) {
            stringRedisTemplate.delete(metaKey(shareId));
            stringRedisTemplate.opsForZSet().remove(KEY_INDEX_CREATED, shareId);
            return Optional.empty();
        }

        if (incrementViewCount) {
            Long latest = stringRedisTemplate.opsForHash()
                    .increment(metaKey(shareId), "viewCount", 1);
            stringRedisTemplate.opsForHash().increment(KEY_STATS, "totalViews", 1);
            stringRedisTemplate.opsForSet().add(KEY_DIRTY_VIEW, shareId);
            if (latest != null) {
                share.setViewCount(latest.intValue());
            }
        }

        fillRemainingFromRedisTtl(shareId, share);
        return Optional.of(share);
    }

    private void writeShareToRedis(ShareContent share) {
        String key = metaKey(share.getShareId());
        Map<String, String> data = toHash(share);
        if (data.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForHash().putAll(key, data);

        long ttlSeconds = computeRemainingSeconds(share);
        if (ttlSeconds > 0) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            stringRedisTemplate.opsForZSet().add(
                    KEY_INDEX_CREATED,
                    share.getShareId(),
                    toEpochMillis(share.getCreateTime())
            );
        } else {
            stringRedisTemplate.delete(key);
            stringRedisTemplate.opsForZSet().remove(KEY_INDEX_CREATED, share.getShareId());
        }
    }

    private List<String> getAllShareIds(String sort) {
        Set<String> ids;
        if ("createTimeAsc".equalsIgnoreCase(sort)) {
            ids = stringRedisTemplate.opsForZSet().range(KEY_INDEX_CREATED, 0, -1);
        } else {
            ids = stringRedisTemplate.opsForZSet().reverseRange(KEY_INDEX_CREATED, 0, -1);
        }
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(ids);
    }

    private AdminSharesPageResponse querySharesFromBackup(int page, int size, String type, String sort) {
        List<ShareContent> shares = backupStorage.getAllShares().stream()
                .filter(share -> matchesTypeFilter(share, type))
                .sorted((a, b) -> {
                    LocalDateTime at = Optional.ofNullable(a.getCreateTime()).orElse(LocalDateTime.MIN);
                    LocalDateTime bt = Optional.ofNullable(b.getCreateTime()).orElse(LocalDateTime.MIN);
                    return "createTimeAsc".equalsIgnoreCase(sort) ? at.compareTo(bt) : bt.compareTo(at);
                })
                .peek(this::normalizeShare)
                .peek(this::fillRemaining)
                .collect(Collectors.toList());

        long totalItems = shares.size();
        int totalPages = (int) Math.ceil(totalItems / (double) size);
        int fromIndex = Math.min(page * size, shares.size());
        int toIndex = Math.min(fromIndex + size, shares.size());
        List<ShareContent> pageItems = shares.subList(fromIndex, toIndex);

        long file = shares.stream().filter(ShareContent::isFile).count();
        long text = shares.size() - file;
        long totalViews = shares.stream().mapToLong(ShareContent::getViewCount).sum();

        return new AdminSharesPageResponse(
                pageItems,
                page,
                size,
                totalItems,
                totalPages,
                new AdminShareStats(totalItems, file, text, totalViews)
        );
    }

    private AdminShareStats queryStatsFromRedis(List<ShareContent> fallbackShares) {
        Map<Object, Object> stats = stringRedisTemplate.opsForHash().entries(KEY_STATS);
        if (stats == null || stats.isEmpty()) {
            long total = fallbackShares.size();
            long file = fallbackShares.stream().filter(ShareContent::isFile).count();
            long text = total - file;
            long totalViews = fallbackShares.stream().mapToLong(ShareContent::getViewCount).sum();
            return new AdminShareStats(total, file, text, totalViews);
        }
        return new AdminShareStats(
                parseLong(stats.get("total"), fallbackShares.size()),
                parseLong(stats.get("file"), fallbackShares.stream().filter(ShareContent::isFile).count()),
                parseLong(stats.get("text"), fallbackShares.stream().filter(s -> !s.isFile()).count()),
                parseLong(stats.get("totalViews"), fallbackShares.stream().mapToLong(ShareContent::getViewCount).sum())
        );
    }

    private void incrementStatsOnCreate(ShareContent share) {
        stringRedisTemplate.opsForHash().increment(KEY_STATS, "total", 1);
        stringRedisTemplate.opsForHash().increment(KEY_STATS, share.isFile() ? "file" : "text", 1);
        stringRedisTemplate.opsForHash().increment(KEY_STATS, "totalViews", Math.max(share.getViewCount(), 0));
    }

    private void decrementStatsOnDelete(ShareContent share) {
        stringRedisTemplate.opsForHash().increment(KEY_STATS, "total", -1);
        stringRedisTemplate.opsForHash().increment(KEY_STATS, share.isFile() ? "file" : "text", -1);
        stringRedisTemplate.opsForHash().increment(KEY_STATS, "totalViews", -Math.max(share.getViewCount(), 0));
    }

    private Map<String, String> toHash(ShareContent share) {
        Map<String, String> map = new LinkedHashMap<>();
        putIfNotNull(map, "shareId", share.getShareId());
        map.put("file", String.valueOf(share.isFile()));
        putIfNotNull(map, "fileName", share.getFileName());
        putIfNotNull(map, "contentType", share.getContentType());
        map.put("size", String.valueOf(share.getSize()));
        putIfNotNull(map, "textContent", share.getTextContent());
        map.put("richText", String.valueOf(share.isRichText()));
        putIfNotNull(map, "createTime", formatDateTime(share.getCreateTime()));
        putIfNotNull(map, "expireTime", formatDateTime(share.getExpireTime()));
        map.put("viewCount", String.valueOf(share.getViewCount()));
        putIfNotNull(map, "filePath", share.getFilePath());
        putIfNotNull(map, "retentionPolicy", share.getRetentionPolicy());
        return map;
    }

    private ShareContent fromHash(Map<Object, Object> raw) {
        ShareContent share = new ShareContent();
        share.setShareId(str(raw.get("shareId")));
        share.setFile(Boolean.parseBoolean(str(raw.get("file"))));
        share.setFileName(str(raw.get("fileName")));
        share.setContentType(str(raw.get("contentType")));
        share.setSize(parseLong(raw.get("size"), 0L));
        share.setTextContent(str(raw.get("textContent")));
        share.setRichText(Boolean.parseBoolean(str(raw.get("richText"))));
        share.setCreateTime(parseDateTime(str(raw.get("createTime"))));
        share.setExpireTime(parseDateTime(str(raw.get("expireTime"))));
        share.setViewCount((int) parseLong(raw.get("viewCount"), 0L));
        share.setFilePath(str(raw.get("filePath")));
        share.setRetentionPolicy(str(raw.get("retentionPolicy")));
        return share;
    }

    private void normalizeShare(ShareContent share) {
        if (share.getCreateTime() == null) {
            share.setCreateTime(LocalDateTime.now());
        }
        if (share.getExpireTime() == null) {
            share.setExpireTime(share.getCreateTime().plusHours(24));
        }
        if (share.getRetentionPolicy() == null || share.getRetentionPolicy().isBlank()) {
            long days = Duration.between(share.getCreateTime(), share.getExpireTime()).toDays();
            share.setRetentionPolicy(days >= 30 ? "D30" : "LEGACY_24H");
        }
        if (share.getViewCount() < 0) {
            share.setViewCount(0);
        }
    }

    private void fillRemaining(ShareContent share) {
        long remainingSeconds = Math.max(computeRemainingSeconds(share), 0);
        int remainingDays = (int) Math.ceil(remainingSeconds / 86400.0);
        share.setRemainingSeconds(remainingSeconds);
        share.setRemainingDays(Math.max(remainingDays, 0));
    }

    private void fillRemainingFromRedisTtl(String shareId, ShareContent share) {
        Long ttl = stringRedisTemplate.getExpire(metaKey(shareId), TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            fillRemaining(share);
            return;
        }
        long remainingSeconds = Math.max(ttl, 0L);
        int remainingDays = (int) Math.ceil(remainingSeconds / 86400.0);
        share.setRemainingSeconds(remainingSeconds);
        share.setRemainingDays(Math.max(remainingDays, 0));
        if (share.getExpireTime() == null) {
            share.setExpireTime(LocalDateTime.now().plusSeconds(remainingSeconds));
        }
    }

    private long computeRemainingSeconds(ShareContent share) {
        normalizeShare(share);
        return Duration.between(LocalDateTime.now(), share.getExpireTime()).getSeconds();
    }

    private boolean isExpired(ShareContent share) {
        normalizeShare(share);
        return LocalDateTime.now().isAfter(share.getExpireTime());
    }

    private boolean matchesTypeFilter(ShareContent share, String type) {
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) {
            return true;
        }
        if ("file".equalsIgnoreCase(type)) {
            return share.isFile();
        }
        if ("text".equalsIgnoreCase(type)) {
            return !share.isFile();
        }
        return true;
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0;
        }
        return dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String metaKey(String shareId) {
        return KEY_META_PREFIX + shareId;
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? null : ISO.format(time);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, ISO);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long parseLong(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void putIfNotNull(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private void onRedisFailure(String message, Exception e) {
        if (e instanceof DataAccessException) {
            log.warn("{}: {}", message, e.getMessage());
            return;
        }
        log.warn("{}: {}", message, e.toString());
    }
}
