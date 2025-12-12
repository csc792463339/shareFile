package cn.hellocsc.storage;

import cn.hellocsc.model.ShareContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 持久化存储功能验证
 * 通过配置 app.storage.verify=true 来启用
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.verify", havingValue = "true")
public class PersistentStorageVerification implements CommandLineRunner {

    @Autowired
    private PersistentTextStorage persistentTextStorage;

    @Override
    public void run(String... args) {
        log.info("开始持久化存储功能验证");

        try {
            // 测试1: 基本保存和读取
            testBasicSaveAndGet();

            // 测试2: 数据更新
            testUpdate();

            // 测试3: 数据失效
            testInvalidation();

            log.info("✅ 持久化存储功能验证通过");

        } catch (Exception e) {
            log.error("❌ 持久化存储功能验证失败", e);
        }
    }

    private void testBasicSaveAndGet() {
        log.info("测试基本保存和读取功能...");

        ShareContent content = new ShareContent();
        content.setShareId("TEST");
        content.setFile(false);
        content.setTextContent("验证测试内容");
        content.setContentType("text/plain");
        content.setSize(8);
        content.setCreateTime(LocalDateTime.now());
        content.setViewCount(0);
        content.setRichText(false);

        // 保存
        persistentTextStorage.save(content);
        log.info("✓ 保存测试数据成功");

        // 读取
        Optional<ShareContent> retrieved = persistentTextStorage.get("TEST");
        if (retrieved.isPresent() && "验证测试内容".equals(retrieved.get().getTextContent())) {
            log.info("✓ 读取测试数据成功");
        } else {
            throw new RuntimeException("读取测试数据失败");
        }
    }

    private void testUpdate() {
        log.info("测试数据更新功能...");

        Optional<ShareContent> content = persistentTextStorage.get("TEST");
        if (content.isPresent()) {
            ShareContent updated = content.get();
            updated.setViewCount(5);
            updated.setTextContent("更新后的内容");

            persistentTextStorage.save(updated);
            log.info("✓ 更新测试数据成功");

            // 验证更新
            Optional<ShareContent> retrieved = persistentTextStorage.get("TEST");
            if (retrieved.isPresent() &&
                retrieved.get().getViewCount() == 5 &&
                "更新后的内容".equals(retrieved.get().getTextContent())) {
                log.info("✓ 验证更新数据成功");
            } else {
                throw new RuntimeException("验证更新数据失败");
            }
        }
    }

    private void testInvalidation() {
        log.info("测试数据失效功能...");

        // 失效数据
        persistentTextStorage.invalidate("TEST");
        log.info("✓ 失效测试数据成功");

        // 验证失效
        Optional<ShareContent> retrieved = persistentTextStorage.get("TEST");
        if (!retrieved.isPresent()) {
            log.info("✓ 验证数据失效成功");
        } else {
            throw new RuntimeException("验证数据失效失败");
        }
    }
}
