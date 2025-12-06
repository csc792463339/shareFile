package cn.hellocsc.task;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hellocsc.service.ShareService;
import cn.hellocsc.storage.MemoryTextStorage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupTask {

    private final ShareService shareService;
    private final MemoryTextStorage memoryTextStorage;

    // 每5分钟执行一次清理
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredShares() {
        log.info("开始清理过期分享内容...");
        int cleaned = shareService.cleanupExpiredShares();
        log.info("清理完成，共删除 {} 个过期分享", cleaned);
    }
}