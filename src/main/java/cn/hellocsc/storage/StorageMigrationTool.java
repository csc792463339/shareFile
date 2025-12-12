package cn.hellocsc.storage;

import cn.hellocsc.model.ShareContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 数据迁移工具 - 从内存存储迁移到持久化存储
 * 通过配置 app.storage.migrate=true 来启用
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.migrate", havingValue = "true")
public class StorageMigrationTool implements CommandLineRunner {

    private final MemoryTextStorage memoryTextStorage;
    private final PersistentTextStorage persistentTextStorage;

    @Override
    public void run(String... args) {
        log.info("开始数据迁移：从内存存储迁移到持久化存储");

        int migratedCount = 0;

        // 这里需要根据实际的MemoryTextStorage实现来获取所有数据
        // 由于Caffeine缓存没有直接的方法获取所有键，我们可能需要修改MemoryTextStorage
        // 或者通过其他方式实现迁移

        log.info("数据迁移完成，共迁移 {} 条记录", migratedCount);
    }
}
