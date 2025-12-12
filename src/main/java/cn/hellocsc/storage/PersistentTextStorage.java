package cn.hellocsc.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cn.hellocsc.model.ShareContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
public class PersistentTextStorage implements InitializingBean, DisposableBean {

    @Value("${app.storage.metadata-file:./data/shares_metadata.json}")
    private String metadataFilePath;

    // 内存缓存，保持高性能访问
    private final Cache<String, ShareContent> memoryCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(5000)
            .build();

    // 用于异步写入的线程池
    private final ScheduledExecutorService writeExecutor = Executors.newSingleThreadScheduledExecutor(
        r -> new Thread(r, "share-metadata-writer"));

    // 读写锁，确保数据一致性
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // JSON序列化器
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 标记是否有待写入的数据
    private volatile boolean hasChanges = false;

    @Override
    public void afterPropertiesSet() {
        // 配置ObjectMapper以处理LocalDateTime
        objectMapper.registerModule(new JavaTimeModule());

        // 确保存储目录存在
        createStorageDirectory();

        // 加载已有数据
        loadFromFile();

        // 启动定期写入任务（每30秒检查一次）
        writeExecutor.scheduleWithFixedDelay(this::flushToDisk, 30, 30, TimeUnit.SECONDS);

        log.info("持久化文本存储初始化完成，元数据文件: {}", metadataFilePath);
    }

    @Override
    public void destroy() {
        // 应用关闭时立即保存所有数据
        flushToDisk();
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("持久化文本存储已关闭");
    }

    public void save(ShareContent content) {
        lock.writeLock().lock();
        try {
            // 先保存到内存缓存，保证读取性能
            memoryCache.put(content.getShareId(), content);
            // 标记有变更
            hasChanges = true;

            log.debug("保存分享记录到内存缓存: {}", content.getShareId());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<ShareContent> get(String shareId) {
        lock.readLock().lock();
        try {
            // 优先从内存缓存获取，保证性能
            ShareContent content = memoryCache.getIfPresent(shareId);
            if (content != null) {
                // 检查是否过期
                if (isExpired(content)) {
                    memoryCache.invalidate(shareId);
                    hasChanges = true;
                    return Optional.empty();
                }
                return Optional.of(content);
            }

            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void invalidate(String shareId) {
        lock.writeLock().lock();
        try {
            memoryCache.invalidate(shareId);
            hasChanges = true;
            log.debug("使分享记录失效: {}", shareId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void cleanUp() {
        lock.writeLock().lock();
        try {
            // 触发Caffeine的清理
            memoryCache.cleanUp();
            hasChanges = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 异步将数据刷新到磁盘
     */
    private void flushToDisk() {
        if (!hasChanges) {
            return;
        }

        lock.readLock().lock();
        Map<String, ShareContent> currentData;
        try {
            // 快速获取当前所有数据的快照
            currentData = new ConcurrentHashMap<>();
            memoryCache.asMap().forEach((key, value) -> {
                if (!isExpired(value)) {
                    currentData.put(key, value);
                }
            });
            hasChanges = false;
        } finally {
            lock.readLock().unlock();
        }

        // 异步写入文件
        try {
            writeToFile(currentData);
            log.debug("成功将 {} 条记录写入持久化文件", currentData.size());
        } catch (Exception e) {
            log.error("写入持久化文件失败", e);
            // 如果写入失败，重新标记为有变更
            hasChanges = true;
        }
    }

    /**
     * 从文件加载数据到内存缓存
     */
    private void loadFromFile() {
        Path filePath = Paths.get(metadataFilePath);
        if (!Files.exists(filePath)) {
            log.info("持久化文件不存在，从空状态开始: {}", metadataFilePath);
            return;
        }

        try {
            String jsonContent = Files.readString(filePath, StandardCharsets.UTF_8);
            if (jsonContent.trim().isEmpty()) {
                log.info("持久化文件为空，从空状态开始");
                return;
            }

            TypeReference<Map<String, ShareContent>> typeRef = new TypeReference<>() {};
            Map<String, ShareContent> loadedData = objectMapper.readValue(jsonContent, typeRef);

            int loadedCount = 0;
            int expiredCount = 0;

            for (Map.Entry<String, ShareContent> entry : loadedData.entrySet()) {
                ShareContent content = entry.getValue();
                if (!isExpired(content)) {
                    memoryCache.put(entry.getKey(), content);
                    loadedCount++;
                } else {
                    expiredCount++;
                }
            }

            log.info("从持久化文件加载数据完成 - 有效记录: {}, 过期记录: {}", loadedCount, expiredCount);

            // 如果有过期记录被过滤掉，标记需要更新文件
            if (expiredCount > 0) {
                hasChanges = true;
            }

        } catch (Exception e) {
            log.error("从持久化文件加载数据失败: " + metadataFilePath, e);
        }
    }

    /**
     * 将数据写入文件
     */
    private void writeToFile(Map<String, ShareContent> data) throws IOException {
        Path filePath = Paths.get(metadataFilePath);
        Path tempPath = Paths.get(metadataFilePath + ".tmp");

        // 先写入临时文件
        try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
            objectMapper.writeValue(writer, data);
        }

        // 原子性地替换文件
        Files.move(tempPath, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 创建存储目录
     */
    private void createStorageDirectory() {
        try {
            Path dir = Paths.get(metadataFilePath).getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("创建存储目录: {}", dir);
            }
        } catch (IOException e) {
            log.error("创建存储目录失败", e);
            throw new RuntimeException("无法创建存储目录", e);
        }
    }

    /**
     * 检查内容是否过期
     */
    private boolean isExpired(ShareContent content) {
        if (content == null || content.getCreateTime() == null) {
            return true;
        }
        LocalDateTime expiryTime = content.getCreateTime().plusHours(24);
        return LocalDateTime.now().isAfter(expiryTime);
    }
}
