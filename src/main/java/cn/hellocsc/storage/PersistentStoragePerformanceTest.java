package cn.hellocsc.storage;

import cn.hellocsc.model.ShareContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 持久化存储性能测试
 * 通过配置 app.storage.performance-test=true 来启用
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.performance-test", havingValue = "true")
public class PersistentStoragePerformanceTest implements CommandLineRunner {

    @Autowired
    private PersistentTextStorage persistentTextStorage;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始持久化存储性能测试");

        // 测试参数
        int threadCount = 10;
        int operationsPerThread = 1000;

        testWritePerformance(threadCount, operationsPerThread);
        testReadPerformance(threadCount, operationsPerThread);

        log.info("持久化存储性能测试完成");
    }

    private void testWritePerformance(int threadCount, int operationsPerThread) throws InterruptedException {
        log.info("测试写入性能 - 线程数: {}, 每线程操作数: {}", threadCount, operationsPerThread);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger counter = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    ShareContent content = createTestContent(counter.incrementAndGet());
                    persistentTextStorage.save(content);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        int totalOps = threadCount * operationsPerThread;

        log.info("写入性能测试完成 - 总操作数: {}, 耗时: {}ms, TPS: {}",
                totalOps, duration, (totalOps * 1000L) / duration);
    }

    private void testReadPerformance(int threadCount, int operationsPerThread) throws InterruptedException {
        log.info("测试读取性能 - 线程数: {}, 每线程操作数: {}", threadCount, operationsPerThread);

        // 先写入一些测试数据
        for (int i = 1; i <= 100; i++) {
            ShareContent content = createTestContent(i);
            persistentTextStorage.save(content);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    int id = (j % 100) + 1;
                    persistentTextStorage.get(String.format("%04d", id));
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        int totalOps = threadCount * operationsPerThread;

        log.info("读取性能测试完成 - 总操作数: {}, 耗时: {}ms, TPS: {}",
                totalOps, duration, (totalOps * 1000L) / duration);
    }

    private ShareContent createTestContent(int id) {
        ShareContent content = new ShareContent();
        content.setShareId(String.format("%04d", id));
        content.setFile(false);
        content.setTextContent("测试内容 " + id);
        content.setContentType("text/plain");
        content.setSize(100);
        content.setCreateTime(LocalDateTime.now());
        content.setViewCount(0);
        content.setRichText(false);
        return content;
    }
}
